package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.*;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;

public class MavenMetadataExtractor {

    public PomInfo extract(JarFile jarFile) {
        Map<String, PomInfo> candidates = new LinkedHashMap<>();

        // 1. Look for all pom.properties files. Fat/assembly jars may contain
        // dependency metadata before the application module metadata.
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("META-INF/maven/") && name.endsWith("pom.properties")) {
                mergeCandidate(candidates, parsePomProperties(jarFile, entry));
            }
        }

        // 2. Merge all embedded pom.xml files with matching pom.properties.
        entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("META-INF/maven/") && name.endsWith("pom.xml") && !name.contains("/target/")) {
                mergeCandidate(candidates, parsePomXml(jarFile, entry));
            }
        }

        return selectPrimaryPom(candidates.values(), jarFile);
    }

    private void mergeCandidate(Map<String, PomInfo> candidates, PomInfo candidate) {
        if (candidate == null || !candidate.hasCoordinates()) {
            return;
        }
        String key = candidate.getGroupId() + ":" + candidate.getArtifactId();
        PomInfo existing = candidates.get(key);
        if (existing == null) {
            candidates.put(key, candidate);
            return;
        }
        mergePomInfo(existing, candidate);
    }

    private void mergePomInfo(PomInfo target, PomInfo source) {
        if (target.getGroupId() == null) target.setGroupId(source.getGroupId());
        if (target.getArtifactId() == null) target.setArtifactId(source.getArtifactId());
        if (target.getVersion() == null) target.setVersion(source.getVersion());
        if (target.getPackaging() == null) target.setPackaging(source.getPackaging());
        if (target.getParentGroupId() == null) target.setParentGroupId(source.getParentGroupId());
        if (target.getParentArtifactId() == null) target.setParentArtifactId(source.getParentArtifactId());
        if (target.getParentVersion() == null) target.setParentVersion(source.getParentVersion());
        if (target.getParentRelativePath() == null) target.setParentRelativePath(source.getParentRelativePath());
        target.getProperties().putAll(source.getProperties());
        for (MavenDependency dep : source.getDependencyManagement()) {
            if (!containsDep(target.getDependencyManagement(), dep)) {
                target.getDependencyManagement().add(dep);
            }
        }
        target.getRepositories().addAll(source.getRepositories());
        target.getPluginRepositories().addAll(source.getPluginRepositories());
        target.getBuildPlugins().addAll(source.getBuildPlugins());
        target.getProfilesXml().addAll(source.getProfilesXml());
        for (MavenDependency dep : source.getDependencies()) {
            if (!containsDep(target.getDependencies(), dep)) {
                dep.setConfidence(MavenDependency.Confidence.HIGH);
                target.getDependencies().add(dep);
            }
        }
    }

    private PomInfo selectPrimaryPom(Collection<PomInfo> candidates, JarFile jarFile) {
        PomInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        String fileArtifactId = inferArtifactId(jarFile.getName());
        String fileVersion = inferVersion(jarFile.getName());
        for (PomInfo candidate : candidates) {
            int score = 0;
            if (equalsIgnoreCase(candidate.getArtifactId(), fileArtifactId)) {
                score += 100;
            }
            if (equalsIgnoreCase(candidate.getVersion(), fileVersion)) {
                score += 20;
            }
            score += Math.min(30, countMatchingClassPrefix(jarFile, candidate));
            if (best == null || score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private int countMatchingClassPrefix(JarFile jarFile, PomInfo candidate) {
        if (candidate == null || candidate.getGroupId() == null) {
            return 0;
        }
        String prefix = candidate.getGroupId().replace('.', '/') + "/";
        int count = 0;
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.endsWith(".class") && name.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    private String inferArtifactId(String jarName) {
        String base = stripArchiveSuffix(jarName);
        int split = findVersionSplit(base);
        return split > 0 ? base.substring(0, split) : base;
    }

    private String inferVersion(String jarName) {
        String base = stripArchiveSuffix(jarName);
        int split = findVersionSplit(base);
        return split > 0 ? base.substring(split + 1) : null;
    }

    private String stripArchiveSuffix(String jarName) {
        String base = new File(jarName).getName();
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private int findVersionSplit(String base) {
        for (int i = base.length() - 1; i >= 0; i--) {
            if (base.charAt(i) == '-') {
                String version = base.substring(i + 1);
                if (!version.isEmpty() && Character.isDigit(version.charAt(0))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private PomInfo parsePomProperties(JarFile jarFile, JarEntry entry) {
        try (InputStream is = jarFile.getInputStream(entry)) {
            Properties props = new Properties();
            props.load(is);

            PomInfo info = new PomInfo();
            info.setGroupId(props.getProperty("groupId"));
            info.setArtifactId(props.getProperty("artifactId"));
            info.setVersion(props.getProperty("version"));

            // Extract parent path info as fallback
            String path = entry.getName();
            // META-INF/maven/{groupId}/{artifactId}/pom.properties
            String[] parts = path.split("/");
            if (parts.length >= 4) {
                if (info.getGroupId() == null) info.setGroupId(parts[2]);
                if (info.getArtifactId() == null) info.setArtifactId(parts[3]);
            }

            return info;
        } catch (IOException e) {
            return null;
        }
    }

    private PomInfo parsePomXml(JarFile jarFile, JarEntry entry) {
        try (InputStream is = jarFile.getInputStream(entry)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();

            PomInfo info = new PomInfo();
            Element root = doc.getDocumentElement();

            info.setGroupId(getDirectTextContent(root, "groupId"));
            info.setArtifactId(getDirectTextContent(root, "artifactId"));
            info.setVersion(getDirectTextContent(root, "version"));
            info.setPackaging(getDirectTextContent(root, "packaging"));

            parseParent(root, info);

            // If own coordinates are missing, inherit from parent
            if (info.getGroupId() == null) info.setGroupId(info.getParentGroupId());
            if (info.getVersion() == null) info.setVersion(info.getParentVersion());
            if (info.getPackaging() == null) info.setPackaging("jar");

            parseProperties(root, info);
            parseDependencies(root, info.getDependencies());
            parseDependencyManagement(root, info);
            parseRepositories(root, "repositories", "repository", info.getRepositories());
            parseRepositories(root, "pluginRepositories", "pluginRepository", info.getPluginRepositories());
            parseBuildPlugins(root, info);
            parseProfiles(root, info);

            return info;
        } catch (Exception e) {
            return null;
        }
    }

    private void parseParent(Element root, PomInfo info) {
        Element parent = getDirectChild(root, "parent");
        if (parent == null) {
            return;
        }
        info.setParentGroupId(getDirectTextContent(parent, "groupId"));
        info.setParentArtifactId(getDirectTextContent(parent, "artifactId"));
        info.setParentVersion(getDirectTextContent(parent, "version"));
        info.setParentRelativePath(getDirectTextContent(parent, "relativePath"));
    }

    private void parseProperties(Element root, PomInfo info) {
        Element properties = getDirectChild(root, "properties");
        if (properties == null) {
            return;
        }
        for (Element child : directChildren(properties)) {
            String text = child.getTextContent();
            if (text != null && !text.trim().isEmpty()) {
                info.getProperties().put(localName(child), text.trim());
            }
        }
    }

    private void parseDependencies(Element root, List<MavenDependency> output) {
        Element dependencies = getDirectChild(root, "dependencies");
        if (dependencies == null) {
            return;
        }
        for (Element depElem : directChildren(dependencies, "dependency")) {
            MavenDependency dep = parseDependency(depElem);
            if (dep != null) {
                output.add(dep);
            }
        }
    }

    private void parseDependencyManagement(Element root, PomInfo info) {
        Element dependencyManagement = getDirectChild(root, "dependencyManagement");
        if (dependencyManagement == null) {
            return;
        }
        Element dependencies = getDirectChild(dependencyManagement, "dependencies");
        if (dependencies == null) {
            return;
        }
        for (Element depElem : directChildren(dependencies, "dependency")) {
            MavenDependency dep = parseDependency(depElem);
            if (dep != null) {
                info.getDependencyManagement().add(dep);
            }
        }
    }

    private MavenDependency parseDependency(Element depElem) {
        String dg = getDirectTextContent(depElem, "groupId");
        String da = getDirectTextContent(depElem, "artifactId");
        String dv = getDirectTextContent(depElem, "version");
        String ds = getDirectTextContent(depElem, "scope");
        String dt = getDirectTextContent(depElem, "type");
        if (dg == null || da == null) {
            return null;
        }

        MavenDependency dep = new MavenDependency(dg, da, dv != null ? dv : "unknown",
                MavenDependency.Confidence.HIGH);
        if (ds != null && !ds.isEmpty()) {
            dep.setScope(ds);
        }
        if (dt != null && !dt.isEmpty()) {
            dep.setType(dt);
        }
        return dep;
    }

    private void parseRepositories(Element root, String containerName, String entryName, List<RepositoryInfo> output) {
        Element repositories = getDirectChild(root, containerName);
        if (repositories == null) {
            return;
        }
        for (Element repositoryElement : directChildren(repositories, entryName)) {
            RepositoryInfo repository = new RepositoryInfo();
            repository.setId(getDirectTextContent(repositoryElement, "id"));
            repository.setUrl(getDirectTextContent(repositoryElement, "url"));
            Element releases = getDirectChild(repositoryElement, "releases");
            Element snapshots = getDirectChild(repositoryElement, "snapshots");
            if (releases != null) {
                repository.setReleasesXml(nodeToXml(releases));
            }
            if (snapshots != null) {
                repository.setSnapshotsXml(nodeToXml(snapshots));
            }
            output.add(repository);
        }
    }

    private void parseBuildPlugins(Element root, PomInfo info) {
        Element build = getDirectChild(root, "build");
        if (build == null) {
            return;
        }
        Element plugins = getDirectChild(build, "plugins");
        if (plugins == null) {
            return;
        }
        for (Element pluginElement : directChildren(plugins, "plugin")) {
            BuildPluginInfo plugin = new BuildPluginInfo();
            plugin.setGroupId(getDirectTextContent(pluginElement, "groupId"));
            plugin.setArtifactId(getDirectTextContent(pluginElement, "artifactId"));
            plugin.setVersion(getDirectTextContent(pluginElement, "version"));

            Element configuration = getDirectChild(pluginElement, "configuration");
            if (configuration != null) {
                plugin.setConfigurationXml(nodeToXml(configuration));
            }

            Element executions = getDirectChild(pluginElement, "executions");
            if (executions != null) {
                for (Element execution : directChildren(executions, "execution")) {
                    plugin.getExecutionsXml().add(nodeToXml(execution));
                }
            }

            info.getBuildPlugins().add(plugin);
        }
    }

    private void parseProfiles(Element root, PomInfo info) {
        Element profiles = getDirectChild(root, "profiles");
        if (profiles == null) {
            return;
        }
        for (Element profile : directChildren(profiles, "profile")) {
            info.getProfilesXml().add(nodeToXml(profile));
        }
    }

    private String getDirectTextContent(Element parent, String tagName) {
        Element child = getDirectChild(parent, tagName);
        if (child != null) {
            String text = child.getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : null;
        }
        return null;
    }

    private Element getDirectChild(Element parent, String tagName) {
        for (Element child : directChildren(parent)) {
            if (tagName.equals(localName(child))) {
                return child;
            }
        }
        return null;
    }

    private List<Element> directChildren(Element parent) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                children.add((Element) node);
            }
        }
        return children;
    }

    private List<Element> directChildren(Element parent, String tagName) {
        List<Element> children = new ArrayList<>();
        for (Element child : directChildren(parent)) {
            if (tagName.equals(localName(child))) {
                children.add(child);
            }
        }
        return children;
    }

    private String localName(Node node) {
        String localName = node.getLocalName();
        return localName != null ? localName : node.getNodeName();
    }

    private String nodeToXml(Node node) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString().trim();
        } catch (Exception e) {
            return node.getTextContent();
        }
    }

    private boolean containsDep(List<MavenDependency> deps, MavenDependency dep) {
        for (MavenDependency d : deps) {
            if (d.getKey().equals(dep.getKey())) return true;
        }
        return false;
    }
}
