package com.z0fsec.jar2mp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourcePostProcessor {

    private static final Pattern DECOMPILER_HEADER = Pattern.compile(
            "\\A\\s*/\\*\\s*\\n(?:\\s*\\*[^\\n]*\\n)*\\s*\\*/\\s*");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([\\w.]+);\\s*$");
    private static final Pattern IMPORT_DECLARATION = Pattern.compile("(?m)^import\\s+([\\w.]+);\\s*\\n");
    private static final Pattern RAW_LIST_ENHANCED_FOR = Pattern.compile(
            "List\\s+(\\w+)\\s*=\\s*([^;]+);\\n(\\s*)for\\s*\\(([^:\\n]+?)\\s+(\\w+)\\s*:\\s*\\1\\)");
    private static final Pattern RAW_COLLECTION_ENHANCED_FOR = Pattern.compile(
            "(List|Set|HashSet|ArrayList)\\s+(\\w+)\\s*=\\s*([^;]+);\\n"
                    + "(\\s*)for\\s*\\(([^:\\n]+?)\\s+(\\w+)\\s*:\\s*\\2\\)");
    private static final Pattern RAW_OPTIONAL_OR_ELSE_THROW = Pattern.compile(
            "Optional\\s+(\\w+)\\s*=\\s*([^;]+);\\n(\\s*)([\\w.$<>]+)\\s+(\\w+)\\s*=\\s*\\(([^)]+)\\)\\1\\.orElseThrow");
    private static final Pattern NUMERIC_GENERIC_COLLECTION_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)([A-Za-z_$][\\w$]*)<\\d+>\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*new\\s+([A-Za-z_$][\\w$]*)<\\d+>\\(([^;\\n]*)\\);");
    private static final Pattern RAW_LIST_CAST_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*\\(List\\)");
    private static final Pattern EXECUTION_EXCEPTION_CAUSE_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)ExecutionException\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]*\\?\\s*\\(Exception\\)[^;\\n]*);");
    private static final Pattern FOR_LOOP_COUNTER_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)for\\s*\\(int\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]+);");
    private static final Pattern UNDEFINED_GENERIC_TO_ARRAY_CAST = Pattern.compile(
            "\\.toArray\\s*\\(\\s*\\(T\\[\\]\\)\\s*new\\s+");
    private static final Pattern ENUM_SET_NONE_OF_WILDCARD_CLASS = Pattern.compile(
            "EnumSet\\.noneOf\\(\\s*(?!\\(Class\\))([^\\)]+?)\\s*\\)");
    private static final Pattern ENUM_VALUE_OF_WILDCARD_CLASS = Pattern.compile(
            "Enum\\.valueOf\\(\\s*(?!\\(Class\\))([A-Za-z_$][\\w$.]*(?:\\[[^\\]]+\\])?)\\s*,");
    private static final Pattern REGEX_TRANSFORMER_CONDITIONAL_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)RegexTransformer\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]*negatableOptionTransformer\\(\\)[^;\\n]*);");
    private static final Pattern QUALIFIED_INNER_INSTANCE_CREATION = Pattern.compile(
            "\\.new\\s+(?:[A-Za-z_$][\\w$]*\\.)+([A-Za-z_$][\\w$]*)\\(");
    private static final Pattern NUMERIC_ANONYMOUS_TYPE_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)\\d+\\s+(\\w+)\\s*=");
    private static final Pattern CFR_VOID_TEMPORARY_LOCAL = Pattern.compile(
            "(?m)^(\\s*)void\\s+([A-Za-z_$][\\w$]*)\\s*;");
    private static final Pattern DECOMPILER_DIAGNOSTIC_BLOCK = Pattern.compile(
            "(?ms)^\\s*/\\*\\s*\\R(?:\\s*\\*\\s*(?:WARNING - [^\\n]*|Enabled force condition propagation|"
                    + "Lifted jumps to return sites|Unable to fully structure code|Loose catch block|"
                    + "Exception performing whole class analysis ignored\\.)\\s*\\R)+"
                    + "\\s*\\*/\\s*\\R?");
    private static final Pattern DECOMPILER_MONITOR_COMMENT = Pattern.compile(
            "(?m)^\\s*// \\*\\* Monitor(?:Enter|Exit)\\[[^\\n]*\\] \\(shouldn't be in output\\)\\s*\\R?");
    private static final Pattern WILDCARD_LAMBDA_PARAMETER = Pattern.compile(
            "^\\?\\s+(?:super|extends)\\s+.+\\s+([A-Za-z_$][\\w$]*)$");
    private static final Pattern NUMERIC_ANONYMOUS_CONSTRUCTOR = Pattern.compile("new\\s+\\d+\\([^;\\n]*\\)");
    private static final Pattern NUMERIC_ANONYMOUS_CAST = Pattern.compile("\\(\\d+\\)\\s*null");
    private static final Pattern SYNTHETIC_ENUM_SWITCH_SELECTOR = Pattern.compile(
            "switch\\s*\\(\\s*1\\.(\\$SwitchMap\\$[A-Za-z0-9_$]+)\\[([\\s\\S]+?)\\.ordinal\\(\\)\\]\\s*\\)");
    private static final Pattern SYNTHETIC_ENUM_SWITCH_CASE = Pattern.compile(
            "(\\bcase\\s+)(\\d+)(\\s*(?::|->))");
    private static final Pattern SYNTHETIC_ENUM_SWITCH_MATCH_EXCEPTION_DEFAULT = Pattern.compile(
            "(?m)^\\s*default\\s*->\\s*throw\\s+new\\s+(?:java\\.lang\\.)?MatchException\\s*\\(\\s*null\\s*,\\s*null\\s*\\)\\s*;\\R?");
    private static final Pattern SWITCH_OPENING_BRACE_WITHOUT_SPACE = Pattern.compile(
            "(?m)^(\\s*(?:return\\s+)?switch\\s*\\([^\\n{]*\\))\\{");
    private static final Pattern METHOD_REFERENCE_TYPE = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::[A-Za-z_$][\\w$]*");
    private static final Pattern LAMBDA_QUERY_WRAPPER_DECLARATION = Pattern.compile(
            "\\bLambdaQueryWrapper\\s+([A-Za-z_$][\\w$]*)\\s*=");
    private static final Pattern LAMBDA_QUERY_WRAPPER_ASSIGNMENT = Pattern.compile(
            "\\b([A-Za-z_$][\\w$]*)\\s*=\\s*(?:\\(LambdaQueryWrapper(?:<[^>]+>)?\\)|new\\s+LambdaQueryWrapper(?:<[^>]+>)?\\()");
    private static final Pattern CFR_LAMBDA_METAFACTORY_METHOD_REFERENCE = Pattern.compile(
            "(?:\\([A-Za-z0-9_$.,<>?\\s&]+\\)\\s*)?"
                    + "LambdaMetafactory\\.(?:altMetafactory|metafactory)\\([^\\n]*?"
                    + "\\b([A-Za-z_$][\\w$]*)\\s*\\(\\s*\\)\\s*,\\s*\\(L([^;\\n)]+);\\)"
                    + "[^\\n]*?\\)\\s*\\(\\s*\\)");
    private static final Pattern SIMPLE_THIS_METHOD_REFERENCE_LAMBDA = Pattern.compile(
            "\\.(map|filter)\\(\\s*([A-Za-z_$][\\w$]*)\\s*->\\s*this\\.([A-Za-z_$][\\w$]*)\\s*\\(\\s*\\2\\s*\\)\\s*\\)");
    private static final Pattern PAGE_INFO_ROW_LIST_METHOD_REFERENCE = Pattern.compile(
            "\\b([A-Za-z_$][\\w$]*)\\.getRowList\\(\\)\\.stream\\(\\)\\.map\\(\\s*"
                    + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::");
    private static final Pattern GENERIC_METHOD_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:(?:public|protected|private|static|final|synchronized)\\s+)*"
                    + "([A-Za-z_$][\\w$.]*\\s*<[^\\n;{}]+>)\\s+([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern PAGE_DATA_METHOD_DECLARATION = Pattern.compile(
            "\\b(?:Result\\s*<\\s*)?PageData\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s*>?\\s+"
                    + "[A-Za-z_$][\\w$]*\\s*\\(");
    private static final Pattern SAME_CLASS_STRING_CONSTANT_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:public|protected|private)?\\s*(?:static\\s+final|final\\s+static)\\s+String\\s+"
                    + "([A-Z][A-Z0-9_]*)\\s*=\\s*(\"(?:\\\\.|[^\"\\\\])*\")\\s*;");
    private static final Pattern LIST_METHOD_DECLARATION = Pattern.compile(
            "\\bList\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s+[A-Za-z_$][\\w$]*\\s*\\(");
    private static final Pattern WRAPPED_LIST_METHOD_DECLARATION = Pattern.compile(
            "\\b[A-Za-z_$][\\w$.]*\\s*<\\s*List\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s*>\\s+"
                    + "[A-Za-z_$][\\w$]*\\s*\\(");
    private static final Pattern TYPED_LIST_DECLARATION = Pattern.compile(
            "\\bList\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s+([A-Za-z_$][\\w$]*)\\b");
    private static final Pattern RAW_LIST_DECLARATION_WITH_INITIALIZER = Pattern.compile(
            "(?m)^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]+;)");
    private static final Pattern MAP_METHOD_DECLARATION = Pattern.compile(
            "\\bMap\\s*<\\s*([^,>]+)\\s*,\\s*([^>]+?)\\s*>\\s+[A-Za-z_$][\\w$]*\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern CHAR_SEQUENCE_SPLIT_ARRAY = Pattern.compile(
            "(?m)^(\\s*)CharSequence\\[\\]\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]*\\.split\\([^;\\n]*\\);)");
    private static final Pattern RAW_ARRAY_LIST_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)ArrayList\\s+([A-Za-z_$][\\w$]*)\\s*;");
    private static final Pattern NO_ARG_CONSTRUCTOR_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:public|protected|private)?\\s+([A-Za-z_$][\\w$]*)\\s*\\(\\s*\\)\\s*\\{");
    private static final String DIAMOND_ELIGIBLE_COLLECTION_CONSTRUCTORS =
            "(?:ArrayDeque|ArrayList|ConcurrentHashMap|ConcurrentSkipListMap|ConcurrentSkipListSet|"
                    + "CopyOnWriteArrayList|HashMap|HashSet|Hashtable|LinkedHashMap|LinkedHashSet|LinkedList|"
                    + "PriorityQueue|Stack|TreeMap|TreeSet|Vector)";
    private static final Pattern RAW_DIAMOND_COLLECTION_CONSTRUCTOR_ASSIGNMENT = Pattern.compile(
            "(?m)^(\\s*(?:(?:public|protected|private|static|final|transient|volatile)\\s+)*"
                    + "[A-Za-z_$][\\w$.]*\\s*<[^;=]+>\\s+"
                    + "[A-Za-z_$][\\w$]*\\s*=\\s*new\\s+)("
                    + DIAMOND_ELIGIBLE_COLLECTION_CONSTRUCTORS
                    + ")(\\s*\\([^;\\n{}]*\\))(?!\\s*\\{)");

    public static Map<String, Map<String, String>> indexMapstructInputTypes(Map<String, String> sources) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, String>> index = new LinkedHashMap<>();
        for (String source : sources.values()) {
            if (source == null || !source.contains("Mapstruct")) {
                continue;
            }
            Matcher interfaceMatcher = Pattern.compile("\\binterface\\s+([A-Za-z_$][\\w$]*Mapstruct)\\b")
                    .matcher(source);
            if (!interfaceMatcher.find()) {
                continue;
            }
            String mapperName = interfaceMatcher.group(1);
            Map<String, String> imports = simpleImports(source);
            Matcher methodMatcher = Pattern.compile("(?m)^\\s*(?:public\\s+)?"
                    + "[A-Za-z_$][\\w$.]*(?:\\s*<[^;()]+>)?\\s+([A-Za-z_$][\\w$]*)\\s*\\(\\s*"
                    + "(?:List\\s*<\\s*)?([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)?)"
                    + "(?:\\s*>\\s*)?\\s+[A-Za-z_$][\\w$]*\\s*\\)\\s*;")
                    .matcher(source);
            Map<String, String> methods = index.computeIfAbsent(mapperName, ignored -> new LinkedHashMap<>());
            while (methodMatcher.find()) {
                String methodName = methodMatcher.group(1);
                String inputType = resolveImportedType(methodMatcher.group(2), imports);
                methods.put(methodName, inputType);
            }
            if (methods.isEmpty()) {
                index.remove(mapperName);
            }
        }
        return index.isEmpty() ? Collections.emptyMap() : index;
    }

    private static Map<String, String> simpleImports(String source) {
        Map<String, String> imports = new LinkedHashMap<>();
        Matcher matcher = IMPORT_DECLARATION.matcher(source);
        while (matcher.find()) {
            String imported = matcher.group(1);
            int lastDot = imported.lastIndexOf('.');
            if (lastDot >= 0) {
                imports.put(imported.substring(lastDot + 1), imported);
            }
        }
        return imports;
    }

    private static String resolveImportedType(String typeName, Map<String, String> imports) {
        if (typeName.contains(".")) {
            return typeName;
        }
        return imports.getOrDefault(typeName, typeName);
    }

    public String process(String source) {
        return process(source, null);
    }

    public String process(String source, String className) {
        return process(source, className, Collections.emptyMap());
    }

    public String process(String source, String className, Map<String, Map<Integer, String>> switchCaseMaps) {
        return process(source, className, switchCaseMaps, Collections.emptyMap());
    }

    public String process(String source, String className, Map<String, Map<Integer, String>> switchCaseMaps,
                          Map<String, Map<String, String>> mapstructInputTypes) {
        return process(source, className, switchCaseMaps, mapstructInputTypes, Collections.emptyMap());
    }

    public String process(String source, String className, Map<String, Map<Integer, String>> switchCaseMaps,
                          Map<String, Map<String, String>> mapstructInputTypes,
                          Map<String, String> localGenericTypes) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        String processed = stripDecompilerHeader(source);
        processed = removeDecompilerDiagnosticComments(processed);
        processed = restorePrintableUnicodeEscapes(processed);
        processed = restoreSwitchBraceSpacing(processed);
        processed = restoreCfrLambdaMetafactoryMethodReferences(processed);
        processed = restoreSimpleThisMethodReferenceLambdas(processed);
        processed = removeRedundantImports(processed, className);
        processed = processed.replace("(Object)", "");
        processed = removeRedundantLiteralCasts(processed);
        processed = removeCollectionUtilityCasts(processed);
        processed = restoreLettuceTimeoutAutounboxing(processed);
        processed = restoreSameClassStringConstantReferences(processed);
        processed = restoreCfrBrokenBreakMarkers(processed);
        processed = removeParameterArrayCasts(processed);
        processed = restoreStringLocalsFromToStringAssignments(processed);
        processed = unwrapSingleElementRedisSetOperationArrays(processed);
        processed = restoreStringSplitArrayTypes(processed);
        processed = removeGuavaListFactoryCasts(processed);
        processed = removeGuavaPartitionListCasts(processed);
        processed = unwrapSFunctionArraySelects(processed);
        processed = removeMyBatisWrapperCasts(processed);
        processed = restoreKnownRawGenericUtilityTypes(processed);
        processed = restoreRawListLongValueOfStreams(processed);
        processed = castGenericReflectionFieldAccess(processed);
        processed = restoreStringListLocalsFromFactoryAssignments(processed);
        processed = addImmutableMapBuilderTypeArguments(processed);
        processed = castOptionalOrElseGetMapSuppliers(processed);
        processed = removeRepeatedValidationAnnotations(processed);
        processed = addListElementTypes(processed);
        processed = addOptionalElementTypes(processed);
        processed = addNumericGenericElementTypes(processed);
        processed = addObjectElementTypesToRawListCasts(processed);
        processed = restoreLambdaQueryWrapperEntityTypes(processed);
        processed = restoreCfrImplicitLocalDeclarations(processed);
        processed = restoreServiceBatchListEntityTypes(processed);
        processed = restoreLambdaUpdateWrapperEntityTypes(processed);
        processed = restoreLambdaQueryChainWrapperEntityTypes(processed);
        processed = restoreLambdaUpdateChainWrapperEntityTypes(processed);
        processed = removeMyBatisWrapperCasts(processed);
        processed = restoreLambdaQueryChainListElementTypes(processed);
        processed = restorePageInfoRowListElementTypes(processed);
        processed = restorePageInfoTypesFromMapstructRows(processed, mapstructInputTypes);
        processed = restorePageInfoTypesFromLocalMapperMethods(processed);
        processed = removeRedundantPageInfoRowListElementCasts(processed);
        processed = restoreLocalTypesFromGenericMethodReturns(processed);
        processed = restorePageInfoLocalsFromPageDataReturnTypes(processed);
        processed = restoreListLocalsFromListReturnTypes(processed);
        processed = restoreListTypesFromStreamMapResults(processed);
        processed = restoreSetTypesFromStreamMapResults(processed);
        processed = restoreStreamCollectListTypes(processed);
        processed = restoreUidSideEffectListTypes(processed);
        processed = restoreRawListTypesFromElementUsage(processed);
        processed = restoreStreamCollectListTypes(processed);
        processed = restoreUidSideEffectListTypes(processed);
        processed = restoreStringObjectMapListTypes(processed);
        processed = restoreNestedListPartitionTypes(processed);
        processed = restoreRawListTypesFromEnhancedForUsage(processed);
        processed = restoreIdentifyMapTypes(processed);
        processed = restoreGetBeansOfTypeMapTypes(processed);
        processed = restoreRawMapTypesFromCollectorsToMap(processed);
        processed = restoreRawMapTypesFromGetOrDefaultDefaults(processed);
        processed = restoreMapValueTypesFromInitializers(processed);
        processed = restoreMapEntryEnhancedForTypes(processed);
        processed = widenShiroFilterMapTypes(processed);
        processed = restoreGenericListMethodTypeVariableLocals(processed);
        processed = restorePageDataLocalsFromResultReturnTypes(processed);
        processed = restoreRawListElementTypesFromStreamMethodReferences(processed);
        processed = alignStreamMethodReferenceOwnersWithListElementTypes(processed);
        processed = widenExecutionExceptionCauseLocals(processed);
        processed = replaceAnsiTextSyntheticOuterReferences(processed);
        processed = hoistForLoopCountersReferencedByLaterLoops(processed);
        processed = removeUndefinedGenericToArrayCasts(processed);
        processed = castClassWildcardsForEnumFactories(processed);
        processed = restoreGenericDefaultExceptionHandlerConstruction(processed);
        processed = widenRegexTransformerConditionals(processed);
        processed = restorePositionalParamSpecListLocals(processed);
        processed = restoreWildcardClassReflectionLocals(processed);
        processed = castWildcardClassArrayElements(processed);
        processed = restoreGenericReturnLocalTypes(processed);
        processed = restoreDeferredAssignments(processed);
        processed = restoreSyntheticEnumSwitches(processed, className, switchCaseMaps);
        processed = removeWildcardBoundsFromLambdaParameters(processed);
        processed = replaceUnavailableAnonymousInnerClasses(processed);
        processed = replaceNumericAnonymousClassFragments(processed);
        processed = shortenQualifiedInnerInstanceCreations(processed);
        processed = removeSyntheticOuterConstructorArguments(processed);
        processed = replaceCfrVoidTemporaryLocals(processed);
        processed = balanceNullArgumentStatements(processed);
        processed = castDoPrivilegedMethodReferences(processed);
        processed = removeUnreachableBreakAfterInfiniteLoop(processed);
        processed = restoreKnownRawGenericUtilityTypes(processed);
        processed = restoreRawListLongValueOfStreams(processed);
        processed = restoreBooleanHandleResultTernaries(processed);
        processed = restorePageDataGetListLocalTypes(processed);
        processed = restorePageDataReturnConstructors(processed);
        processed = alignStreamMethodReferenceOwnersWithListElementTypes(processed);
        processed = restoreRawHashMapTypesFromTypedMethodArguments(processed);
        processed = restoreRawArrayListTypesFromTypedMethodArguments(processed);
        processed = restoreLocalGenericTypesFromBytecode(processed, localGenericTypes);
        processed = restoreRawHashMapTypesFromPutValues(processed);
        processed = restoreDiamondConstructorsForTypedCollectionAssignments(processed);
        processed = removeRedundantKnownVariableCasts(processed);
        processed = wrapRocketMqBatchSyncSendTypedLists(processed);
        processed = restoreCheckedExceptionHandlers(processed);
        processed = restoreSwitchBraceSpacing(processed);
        processed = restoreUnindentedMemberMethodDeclarations(processed);
        return processed;
    }

    private String wrapRocketMqBatchSyncSendTypedLists(String source) {
        if (!source.contains("batchSyncSend(")) {
            return source;
        }
        String[] lines = source.split("\\n", -1);
        Map<String, String> localTypes = new LinkedHashMap<>();
        int methodDepth = 0;
        boolean changed = false;
        Pattern typedLocal = Pattern.compile("^\\s*(?:final\\s+)?([A-Za-z_$][\\w$.]*"
                + "(?:\\s*<[^;=]+>)?(?:\\s*\\[\\])?)\\s+([A-Za-z_$][\\w$]*)\\s*(?:=|;)");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (looksLikeMethodDeclaration(line)) {
                localTypes.clear();
                localTypes.putAll(methodParameterTypes(line));
                methodDepth = Math.max(1, braceDelta(line));
                continue;
            }
            if (methodDepth <= 0) {
                continue;
            }
            String wrapped = wrapRocketMqBatchSyncSendTypedListsFromLine(line, localTypes);
            if (!wrapped.equals(line)) {
                lines[i] = wrapped;
                changed = true;
            }
            Matcher localMatcher = typedLocal.matcher(lines[i]);
            if (localMatcher.find()) {
                localTypes.put(localMatcher.group(2), normalizeGenericType(localMatcher.group(1)));
            }
            methodDepth += braceDelta(line);
            if (methodDepth <= 0) {
                localTypes.clear();
            }
        }
        String processed = String.join("\n", lines);
        return changed ? ensureImport(processed, "java.util.ArrayList") : processed;
    }

    private String wrapRocketMqBatchSyncSendTypedListsFromLine(String line, Map<String, String> localTypes) {
        StringBuilder builder = new StringBuilder(line.length() + 32);
        int searchIndex = 0;
        while (true) {
            int methodIndex = line.indexOf("batchSyncSend(", searchIndex);
            if (methodIndex < 0) {
                builder.append(line, searchIndex, line.length());
                return builder.toString();
            }
            int openParen = line.indexOf('(', methodIndex);
            int closeParen = findMatchingParen(line, openParen);
            if (closeParen < 0) {
                builder.append(line, searchIndex, methodIndex + 1);
                searchIndex = methodIndex + 1;
                continue;
            }
            String arguments = line.substring(openParen + 1, closeParen);
            List<String> splitArguments = splitTopLevelArguments(arguments);
            if (splitArguments.size() >= 2
                    && needsBatchSyncSendListBridge(splitArguments.get(1), localTypes)) {
                splitArguments.set(1, "new ArrayList<Object>(" + splitArguments.get(1).trim() + ")");
                builder.append(line, searchIndex, openParen + 1)
                        .append(String.join(", ", splitArguments))
                        .append(')');
            } else {
                builder.append(line, searchIndex, closeParen + 1);
            }
            searchIndex = closeParen + 1;
        }
    }

    private boolean needsBatchSyncSendListBridge(String argument, Map<String, String> localTypes) {
        String variableName = argument == null ? "" : argument.trim();
        if (!isSimpleIdentifier(variableName)) {
            return false;
        }
        String type = localTypes.get(variableName);
        if (type == null || !"List".equals(rawSimpleTypeName(type))) {
            return false;
        }
        String elementType = firstGenericArgument(type);
        return elementType != null
                && !elementType.isEmpty()
                && !"?".equals(elementType)
                && !"Object".equals(rawSimpleTypeName(elementType));
    }

    private boolean isSimpleIdentifier(String value) {
        if (value == null || value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String firstGenericArgument(String type) {
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');
        if (start < 0 || end <= start) {
            return null;
        }
        List<String> arguments = splitTopLevelArguments(type.substring(start + 1, end));
        return arguments.isEmpty() ? null : normalizeGenericType(arguments.get(0));
    }

    private String removeRedundantKnownVariableCasts(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> localTypes = new LinkedHashMap<>();
        Set<String> catchParameters = new LinkedHashSet<>();
        int methodDepth = 0;
        Pattern typedLocal = Pattern.compile("^\\s*(?:final\\s+)?([A-Za-z_$][\\w$.]*"
                + "(?:\\s*<[^;=]+>)?(?:\\s*\\[\\])?)\\s+([A-Za-z_$][\\w$]*)\\s*(?:=|;)");
        Pattern catchParameter = Pattern.compile("\\bcatch\\s*\\(\\s*([A-Za-z_$][\\w$.]*)\\s+([A-Za-z_$][\\w$]*)\\s*\\)");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (looksLikeMethodDeclaration(line)) {
                localTypes.clear();
                catchParameters.clear();
                localTypes.putAll(methodParameterTypes(line));
                methodDepth = Math.max(1, braceDelta(line));
                continue;
            }
            if (methodDepth <= 0 && looksLikeInitializerDeclaration(line)) {
                localTypes.clear();
                catchParameters.clear();
                methodDepth = Math.max(1, braceDelta(line));
                continue;
            }
            if (methodDepth <= 0) {
                continue;
            }
            Matcher catchMatcher = catchParameter.matcher(line);
            if (catchMatcher.find()) {
                localTypes.put(catchMatcher.group(2), normalizeGenericType(catchMatcher.group(1)));
                catchParameters.add(catchMatcher.group(2));
            }
            lines[i] = removeRedundantKnownVariableCastsFromLine(line, localTypes, catchParameters);
            Matcher localMatcher = typedLocal.matcher(lines[i]);
            if (localMatcher.find()) {
                localTypes.put(localMatcher.group(2), normalizeGenericType(localMatcher.group(1)));
                catchParameters.remove(localMatcher.group(2));
            }
            methodDepth += braceDelta(line);
            if (methodDepth <= 0) {
                localTypes.clear();
                catchParameters.clear();
            }
        }
        return String.join("\n", lines);
    }

    private String removeRedundantKnownVariableCastsFromLine(String line, Map<String, String> localTypes,
                                                            Set<String> catchParameters) {
        Matcher matcher = Pattern.compile("\\(([A-Za-z_$][\\w$.]*(?:\\s*<[^)]*>\\s*)?(?:\\s*\\[\\])?)\\)\\s*"
                + "([A-Za-z_$][\\w$]*)\\b").matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String castType = normalizeGenericType(matcher.group(1));
            String knownType = localTypes.get(matcher.group(2));
            if (knownType != null && (castMatchesKnownType(castType, knownType)
                    || isThrowableCastOfCatchParameter(castType, matcher.group(2), catchParameters))) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(2)));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isThrowableCastOfCatchParameter(String castType, String variableName, Set<String> catchParameters) {
        return catchParameters.contains(variableName) && "Throwable".equals(rawSimpleTypeName(castType));
    }

    private boolean castMatchesKnownType(String castType, String knownType) {
        if (castType.contains("<")) {
            return false;
        }
        String castSimpleType = rawSimpleTypeName(castType);
        String knownSimpleType = rawSimpleTypeName(knownType);
        if ("Throwable".equals(castSimpleType) && isKnownThrowableType(knownSimpleType)) {
            return true;
        }
        return castSimpleType.equals(knownSimpleType);
    }

    private boolean isKnownThrowableType(String simpleTypeName) {
        return "Throwable".equals(simpleTypeName)
                || "Exception".equals(simpleTypeName)
                || "RuntimeException".equals(simpleTypeName);
    }

    private boolean looksLikeInitializerDeclaration(String line) {
        return "static {".equals(line.trim());
    }

    private int braceDelta(String line) {
        int delta = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '\'') {
                inChar = true;
            } else if (current == '{') {
                delta++;
            } else if (current == '}') {
                delta--;
            }
        }
        return delta;
    }

    private String restoreRawHashMapTypesFromPutValues(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> localTypes = new LinkedHashMap<>();
        Map<String, Integer> rawHashMapLines = new LinkedHashMap<>();
        Pattern rawHashMap = Pattern.compile("^(\\s*)HashMap\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "new\\s+HashMap\\s*\\(([^;\\n]*)\\);");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (looksLikeMethodDeclaration(line)) {
                localTypes.clear();
                rawHashMapLines.clear();
                localTypes.putAll(methodParameterTypes(line));
            }

            Matcher typedLocal = Pattern.compile("^\\s*([A-Za-z_$][\\w$.]*(?:\\s*<[^;=]+>)?)\\s+"
                    + "([A-Za-z_$][\\w$]*)\\s*(?:=|;)").matcher(line);
            if (typedLocal.find() && !typedLocal.group(1).equals("HashMap")) {
                localTypes.put(typedLocal.group(2), normalizeGenericType(typedLocal.group(1)));
            }

            Matcher rawMatcher = rawHashMap.matcher(line);
            if (rawMatcher.find()) {
                rawHashMapLines.put(rawMatcher.group(2), i);
                continue;
            }

            for (Map.Entry<String, Integer> entry : new ArrayList<>(rawHashMapLines.entrySet())) {
                String mapName = entry.getKey();
                String[] keyAndValueTypes = inferPutKeyAndValueTypes(line, mapName, localTypes);
                if (keyAndValueTypes == null) {
                    continue;
                }
                int declarationLine = entry.getValue();
                Matcher declarationMatcher = rawHashMap.matcher(lines[declarationLine]);
                if (!declarationMatcher.find()) {
                    rawHashMapLines.remove(mapName);
                    continue;
                }
                String mapType = "HashMap<" + keyAndValueTypes[0] + ", " + keyAndValueTypes[1] + ">";
                lines[declarationLine] = declarationMatcher.replaceFirst(Matcher.quoteReplacement(
                        declarationMatcher.group(1) + mapType + " " + mapName
                                + " = new HashMap(" + declarationMatcher.group(3) + ");"));
                localTypes.put(mapName, mapType);
                rawHashMapLines.remove(mapName);
            }
        }
        return String.join("\n", lines);
    }

    private Map<String, String> methodParameterTypes(String line) {
        Map<String, String> parameterTypes = new LinkedHashMap<>();
        int openParen = line.indexOf('(');
        int closeParen = findMatchingParen(line, openParen);
        if (openParen < 0 || closeParen < 0) {
            return parameterTypes;
        }
        for (String parameter : splitTopLevelArguments(line.substring(openParen + 1, closeParen))) {
            String type = parameterType(parameter);
            String name = parameterName(parameter);
            if (type != null && name != null) {
                parameterTypes.put(name, type);
            }
        }
        return parameterTypes;
    }

    private String parameterName(String parameter) {
        String trimmed = parameter.trim();
        int split = lastTopLevelWhitespace(trimmed);
        if (split < 0 || split + 1 >= trimmed.length()) {
            return null;
        }
        return trimmed.substring(split + 1).replace("...", "").trim();
    }

    private String[] inferPutKeyAndValueTypes(String line, String mapName, Map<String, String> localTypes) {
        int putIndex = line.indexOf(mapName + ".put(");
        if (putIndex < 0) {
            return null;
        }
        int openParen = line.indexOf('(', putIndex);
        int closeParen = findMatchingParen(line, openParen);
        if (openParen < 0 || closeParen < 0) {
            return null;
        }
        List<String> arguments = splitTopLevelArguments(line.substring(openParen + 1, closeParen));
        if (arguments.size() != 2) {
            return null;
        }
        String keyType = inferMapKeyExpressionType(arguments.get(0), localTypes);
        String valueType = inferMapValueExpressionType(arguments.get(1), localTypes);
        return keyType == null || valueType == null ? null : new String[]{keyType, valueType};
    }

    private String inferMapKeyExpressionType(String expression, Map<String, String> localTypes) {
        String trimmed = stripLeadingCasts(expression);
        String localType = localTypes.get(trimmed);
        if (localType != null) {
            return localType;
        }
        String inferred = inferExpressionType(trimmed);
        if (inferred != null) {
            return inferred;
        }
        if (trimmed.matches("Boolean\\.(?:TRUE|FALSE)|\"[^\"]*\"|\\d+L?|\\d+\\.\\d+")) {
            return inferLiteralType(trimmed);
        }
        return null;
    }

    private String inferMapValueExpressionType(String expression, Map<String, String> localTypes) {
        String trimmed = stripLeadingCasts(expression);
        String valueType = localTypes.get(trimmed);
        return "Object".equals(valueType) ? null : valueType;
    }

    private String stripLeadingCasts(String expression) {
        String trimmed = expression.trim();
        while (true) {
            Matcher matcher = Pattern.compile("^\\([A-Za-z_$][\\w$]*(?:\\s*<[^)]*>\\s*)?\\)\\s*(.+)$")
                    .matcher(trimmed);
            if (!matcher.find()) {
                return trimmed;
            }
            trimmed = matcher.group(1).trim();
        }
    }

    private String restoreRawHashMapTypesFromTypedMethodArguments(String source) {
        Map<String, Map<Integer, String>> mapParameterTypes = collectTypedMapParameterTypes(source);
        if (mapParameterTypes.isEmpty()) {
            return source;
        }
        String[] lines = source.split("\\n", -1);
        Pattern rawHashMap = Pattern.compile("^(\\s*)HashMap\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "new\\s+HashMap\\s*\\(([^;\\n]*)\\);");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = rawHashMap.matcher(lines[i]);
            if (!matcher.find()) {
                continue;
            }
            String variableName = matcher.group(2);
            String mapType = findTypedMapArgumentType(lines, i + 1, variableName, mapParameterTypes);
            if (mapType == null) {
                continue;
            }
            String hashMapType = "HashMap" + mapType.substring(mapType.indexOf('<'));
            lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                    matcher.group(1) + hashMapType + " " + variableName
                            + " = new HashMap(" + matcher.group(3) + ");"));
        }
        return String.join("\n", lines);
    }

    private Map<String, Map<Integer, String>> collectTypedMapParameterTypes(String source) {
        Map<String, Map<Integer, String>> parameterTypes = new LinkedHashMap<>();
        String[] lines = source.split("\\n", -1);
        for (String line : lines) {
            if (!looksLikeMethodDeclaration(line)) {
                continue;
            }
            int openParen = line.indexOf('(');
            int closeParen = findMatchingParen(line, openParen);
            if (openParen < 0 || closeParen < 0) {
                continue;
            }
            String beforeParen = line.substring(0, openParen).trim();
            int methodNameStart = beforeParen.lastIndexOf(' ');
            if (methodNameStart < 0) {
                continue;
            }
            String methodName = beforeParen.substring(methodNameStart + 1);
            List<String> parameters = splitTopLevelArguments(line.substring(openParen + 1, closeParen));
            Map<Integer, String> typedParameters = new LinkedHashMap<>();
            for (int i = 0; i < parameters.size(); i++) {
                String parameterType = parameterType(parameters.get(i));
                if (parameterType != null && rawSimpleTypeName(parameterType).equals("Map")
                        && parameterType.contains("<")) {
                    typedParameters.put(i, normalizeGenericType(parameterType));
                }
            }
            if (!typedParameters.isEmpty()) {
                Map<Integer, String> existingParameters =
                        parameterTypes.computeIfAbsent(methodName, ignored -> new LinkedHashMap<>());
                for (Map.Entry<Integer, String> entry : typedParameters.entrySet()) {
                    existingParameters.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }
        return parameterTypes;
    }

    private String findTypedMapArgumentType(String[] lines, int start, String variableName,
                                            Map<String, Map<Integer, String>> mapParameterTypes) {
        for (int i = start; i < lines.length; i++) {
            if (i != start && looksLikeMethodDeclaration(lines[i])) {
                return null;
            }
            for (Map.Entry<String, Map<Integer, String>> entry : mapParameterTypes.entrySet()) {
                String methodName = entry.getKey();
                int searchIndex = 0;
                while (true) {
                    int methodIndex = lines[i].indexOf(methodName + "(", searchIndex);
                    if (methodIndex < 0) {
                        methodIndex = lines[i].indexOf("." + methodName + "(", searchIndex);
                    }
                    if (methodIndex < 0) {
                        break;
                    }
                    int openParen = lines[i].indexOf('(', methodIndex);
                    int closeParen = findMatchingParen(lines[i], openParen);
                    if (closeParen < 0) {
                        break;
                    }
                    List<String> arguments = splitTopLevelArguments(lines[i].substring(openParen + 1, closeParen));
                    for (Map.Entry<Integer, String> typedParameter : entry.getValue().entrySet()) {
                        int parameterIndex = typedParameter.getKey();
                        if (parameterIndex < arguments.size()
                                && argumentReferencesVariable(arguments.get(parameterIndex), variableName)) {
                            return typedParameter.getValue();
                        }
                    }
                    searchIndex = closeParen + 1;
                }
            }
        }
        return null;
    }

    private String parameterType(String parameter) {
        String trimmed = parameter.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        trimmed = trimmed.replaceAll("\\bfinal\\s+", "");
        int split = lastTopLevelWhitespace(trimmed);
        return split < 0 ? null : normalizeGenericType(trimmed.substring(0, split));
    }

    private int lastTopLevelWhitespace(String text) {
        int angleDepth = 0;
        int lastWhitespace = -1;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '<') {
                angleDepth++;
            } else if (current == '>' && angleDepth > 0) {
                angleDepth--;
            } else if (Character.isWhitespace(current) && angleDepth == 0) {
                lastWhitespace = i;
            }
        }
        return lastWhitespace;
    }

    private boolean argumentReferencesVariable(String argument, String variableName) {
        String trimmed = argument.trim();
        Matcher castMatcher = Pattern.compile("^(?:\\([A-Za-z_$][\\w$]*(?:\\s*<[^)]*>\\s*)?\\)\\s*)+(.+)$")
                .matcher(trimmed);
        if (castMatcher.find()) {
            trimmed = castMatcher.group(1).trim();
        }
        return trimmed.equals(variableName);
    }

    private String restoreRawArrayListTypesFromTypedMethodArguments(String source) {
        Map<String, Map<Integer, String>> listParameterTypes = collectTypedListParameterTypes(source);
        if (listParameterTypes.isEmpty()) {
            return source;
        }
        String[] lines = source.split("\\n", -1);
        Pattern rawArrayList = Pattern.compile("^(\\s*)ArrayList\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "(Lists\\.newArrayList\\s*\\(\\s*\\)|new\\s+ArrayList\\s*\\(([^;\\n]*)\\))\\s*;");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = rawArrayList.matcher(lines[i]);
            if (!matcher.find()) {
                continue;
            }
            String variableName = matcher.group(2);
            String listType = findTypedListArgumentType(lines, i + 1, variableName, listParameterTypes);
            if (listType == null) {
                continue;
            }
            String initializer = matcher.group(3);
            if (initializer.startsWith("new ArrayList")) {
                initializer = "new ArrayList<>(" + matcher.group(4) + ")";
            }
            lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                    matcher.group(1) + listType + " " + variableName + " = " + initializer + ";"));
        }
        return String.join("\n", lines);
    }

    private Map<String, Map<Integer, String>> collectTypedListParameterTypes(String source) {
        Map<String, Map<Integer, String>> parameterTypes = new LinkedHashMap<>();
        String[] lines = source.split("\\n", -1);
        for (String line : lines) {
            if (!looksLikeMethodDeclaration(line)) {
                continue;
            }
            int openParen = line.indexOf('(');
            int closeParen = findMatchingParen(line, openParen);
            if (openParen < 0 || closeParen < 0) {
                continue;
            }
            String beforeParen = line.substring(0, openParen).trim();
            int methodNameStart = beforeParen.lastIndexOf(' ');
            if (methodNameStart < 0) {
                continue;
            }
            String methodName = beforeParen.substring(methodNameStart + 1);
            List<String> parameters = splitTopLevelArguments(line.substring(openParen + 1, closeParen));
            Map<Integer, String> typedParameters = new LinkedHashMap<>();
            for (int i = 0; i < parameters.size(); i++) {
                String parameterType = parameterType(parameters.get(i));
                if (parameterType != null && rawSimpleTypeName(parameterType).equals("List")
                        && parameterType.contains("<")) {
                    typedParameters.put(i, normalizeGenericType(parameterType));
                }
            }
            if (!typedParameters.isEmpty()) {
                Map<Integer, String> existingParameters =
                        parameterTypes.computeIfAbsent(methodName, ignored -> new LinkedHashMap<>());
                for (Map.Entry<Integer, String> entry : typedParameters.entrySet()) {
                    existingParameters.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }
        return parameterTypes;
    }

    private String findTypedListArgumentType(String[] lines, int start, String variableName,
                                            Map<String, Map<Integer, String>> listParameterTypes) {
        for (int i = start; i < lines.length; i++) {
            if (i != start && looksLikeMethodDeclaration(lines[i])) {
                return null;
            }
            for (Map.Entry<String, Map<Integer, String>> entry : listParameterTypes.entrySet()) {
                String methodName = entry.getKey();
                int searchIndex = 0;
                while (true) {
                    int methodIndex = lines[i].indexOf(methodName + "(", searchIndex);
                    if (methodIndex < 0) {
                        methodIndex = lines[i].indexOf("." + methodName + "(", searchIndex);
                    }
                    if (methodIndex < 0) {
                        break;
                    }
                    int openParen = lines[i].indexOf('(', methodIndex);
                    int closeParen = findMatchingParen(lines[i], openParen);
                    if (closeParen < 0) {
                        break;
                    }
                    List<String> arguments = splitTopLevelArguments(lines[i].substring(openParen + 1, closeParen));
                    for (Map.Entry<Integer, String> typedParameter : entry.getValue().entrySet()) {
                        int parameterIndex = typedParameter.getKey();
                        if (parameterIndex < arguments.size()
                                && argumentReferencesVariable(arguments.get(parameterIndex), variableName)) {
                            return typedParameter.getValue();
                        }
                    }
                    searchIndex = closeParen + 1;
                }
            }
        }
        return null;
    }

    private String restoreLocalGenericTypesFromBytecode(String source, Map<String, String> localGenericTypes) {
        if (localGenericTypes == null || localGenericTypes.isEmpty()) {
            return source;
        }
        String processed = ensureImportsForLocalGenericTypes(source, localGenericTypes);
        String[] lines = processed.split("\\n", -1);
        Map<String, String> imports = simpleImports(processed);
        String packageName = findPackageName(processed, null);
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) {
            for (Map.Entry<String, String> entry : localGenericTypes.entrySet()) {
                String variableName = entry.getKey();
                String targetType = simplifyJavaType(entry.getValue(), imports, packageName);
                String rewritten = restoreLocalGenericTypeDeclaration(lines[i], variableName, targetType);
                if (!rewritten.equals(lines[i])) {
                    lines[i] = rewritten;
                    changed = true;
                    break;
                }
            }
        }
        return changed ? String.join("\n", lines) : processed;
    }

    private String ensureImportsForLocalGenericTypes(String source, Map<String, String> localGenericTypes) {
        String processed = source;
        String packageName = findPackageName(source, null);
        Map<String, Set<String>> classNamesBySimpleName = referencedClassNamesBySimpleName(source, localGenericTypes);
        for (String javaType : localGenericTypes.values()) {
            for (String typeName : referencedClassNames(javaType)) {
                if (typeName.startsWith("java.lang.")) {
                    continue;
                }
                int lastDot = typeName.lastIndexOf('.');
                if (lastDot < 0) {
                    continue;
                }
                if (!packageName.isEmpty() && typeName.substring(0, lastDot).equals(packageName)) {
                    continue;
                }
                if (hasSimpleNameConflict(typeName, classNamesBySimpleName)) {
                    continue;
                }
                processed = ensureImport(processed, typeName);
            }
        }
        return processed;
    }

    private Map<String, Set<String>> referencedClassNamesBySimpleName(String source,
                                                                      Map<String, String> localGenericTypes) {
        Map<String, Set<String>> namesBySimpleName = new LinkedHashMap<>();
        for (String importedType : simpleImports(source).values()) {
            namesBySimpleName.computeIfAbsent(simpleTypeName(importedType), ignored -> new LinkedHashSet<>())
                    .add(importedType);
        }
        for (String javaType : localGenericTypes.values()) {
            for (String typeName : referencedClassNames(javaType)) {
                namesBySimpleName.computeIfAbsent(simpleTypeName(typeName), ignored -> new LinkedHashSet<>())
                        .add(typeName);
            }
        }
        return namesBySimpleName;
    }

    private boolean hasSimpleNameConflict(String typeName, Map<String, Set<String>> classNamesBySimpleName) {
        Set<String> names = classNamesBySimpleName.get(simpleTypeName(typeName));
        return names != null && (names.size() > 1 || !names.contains(typeName));
    }

    private Set<String> referencedClassNames(String javaType) {
        Set<String> types = new LinkedHashSet<>();
        if (javaType == null || javaType.isEmpty()) {
            return types;
        }
        Matcher matcher = Pattern.compile("\\b(?:[a-z_$][\\w$]*\\.)+[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*")
                .matcher(javaType);
        while (matcher.find()) {
            types.add(matcher.group());
        }
        return types;
    }

    private String restoreLocalGenericTypeDeclaration(String line, String variableName, String targetType) {
        if (targetType == null || !targetType.contains("<")) {
            return line;
        }
        Pattern declaration = Pattern.compile("^(\\s*)((?:final\\s+)?)"
                + "([A-Za-z_$][\\w$.]*)\\s+"
                + Pattern.quote(variableName) + "\\s*=\\s*([^;\\n{}]+);(\\s*)$");
        Matcher matcher = declaration.matcher(line);
        if (!matcher.find()) {
            return line;
        }
        String currentRawType = rawSimpleTypeName(matcher.group(3));
        String targetRawType = rawSimpleTypeName(targetType);
        if (!canRewriteRawGenericType(currentRawType, targetRawType)) {
            return line;
        }
        String initializer = restoreRawCollectionInitializerDiamond(matcher.group(4), currentRawType);
        return matcher.group(1) + matcher.group(2) + targetType + " " + variableName + " = "
                + initializer + ";" + matcher.group(5);
    }

    private boolean canRewriteRawGenericType(String currentRawType, String targetRawType) {
        if (currentRawType.equals(targetRawType)) {
            return true;
        }
        if ("ArrayList".equals(currentRawType) && "List".equals(targetRawType)) {
            return true;
        }
        if ("HashSet".equals(currentRawType) && "Set".equals(targetRawType)) {
            return true;
        }
        return "HashMap".equals(currentRawType) && "Map".equals(targetRawType);
    }

    private String restoreRawCollectionInitializerDiamond(String initializer, String currentRawType) {
        Pattern rawConstructor = Pattern.compile("^new\\s+" + Pattern.quote(currentRawType)
                + "\\s*\\(([^;{}]*)\\)$");
        Matcher matcher = rawConstructor.matcher(initializer.trim());
        if (!matcher.find()) {
            return initializer;
        }
        return "new " + currentRawType + "<>(" + matcher.group(1) + ")";
    }

    private String simplifyJavaType(String javaType, Map<String, String> imports, String packageName) {
        String simplified = javaType.replace("java.lang.", "");
        if (packageName != null && !packageName.isEmpty()) {
            simplified = simplified.replace(packageName + ".", "");
        }
        List<Map.Entry<String, String>> orderedImports = new ArrayList<>(imports.entrySet());
        orderedImports.sort((left, right) -> Integer.compare(right.getValue().length(), left.getValue().length()));
        for (Map.Entry<String, String> entry : orderedImports) {
            simplified = simplified.replace(entry.getValue(), entry.getKey());
        }
        return simplified;
    }

    private String restoreDiamondConstructorsForTypedCollectionAssignments(String source) {
        Matcher matcher = RAW_DIAMOND_COLLECTION_CONSTRUCTOR_ASSIGNMENT.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2) + "<>" + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String removeSyntheticOuterConstructorArguments(String source) {
        Set<String> noArgConstructors = new LinkedHashSet<>();
        Matcher matcher = NO_ARG_CONSTRUCTOR_DECLARATION.matcher(source);
        while (matcher.find()) {
            noArgConstructors.add(matcher.group(1));
        }
        String processed = source;
        for (String constructorName : noArgConstructors) {
            Pattern syntheticInvocation = Pattern.compile("\\bnew\\s+"
                    + "(?:[A-Za-z_$][\\w$]*\\.)*"
                    + Pattern.quote(constructorName)
                    + "\\s*\\(\\s*this\\s*\\)");
            processed = syntheticInvocation.matcher(processed)
                    .replaceAll(Matcher.quoteReplacement("new " + constructorName + "()"));
        }
        return processed;
    }

    private String removeDecompilerDiagnosticComments(String source) {
        String processed = DECOMPILER_DIAGNOSTIC_BLOCK.matcher(source).replaceAll("");
        return DECOMPILER_MONITOR_COMMENT.matcher(processed).replaceAll("");
    }

    private String stripDecompilerHeader(String source) {
        Matcher matcher = DECOMPILER_HEADER.matcher(source);
        if (!matcher.find()) {
            return source;
        }
        String header = matcher.group();
        if (!header.contains("Decompiled with") && !header.contains("Could not load the following classes")) {
            return source;
        }
        return source.substring(matcher.end());
    }

    private String restorePrintableUnicodeEscapes(String source) {
        StringBuilder restored = new StringBuilder(source.length());
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (inLineComment) {
                restored.append(current);
                if (current == '\n') {
                    inLineComment = false;
                }
                index++;
                continue;
            }
            if (inBlockComment) {
                restored.append(current);
                if (current == '*' && next == '/') {
                    restored.append(next);
                    inBlockComment = false;
                    index += 2;
                } else {
                    index++;
                }
                continue;
            }
            if (!inString && !inChar) {
                if (current == '/' && next == '/') {
                    restored.append(current).append(next);
                    inLineComment = true;
                    index += 2;
                    continue;
                }
                if (current == '/' && next == '*') {
                    restored.append(current).append(next);
                    inBlockComment = true;
                    index += 2;
                    continue;
                }
                if (current == '"') {
                    inString = true;
                } else if (current == '\'') {
                    inChar = true;
                }
                restored.append(current);
                index++;
                continue;
            }

            if (current == '\\') {
                UnicodeEscape escape = escaped ? null : unicodeEscapeAt(source, index);
                if (escape != null && isPrintableNonAscii(escape.codePoint)) {
                    restored.appendCodePoint(escape.codePoint);
                    escaped = false;
                    index = escape.endIndex;
                    continue;
                }
                restored.append(current);
                escaped = !escaped;
                index++;
                continue;
            }

            restored.append(current);
            if (!escaped && ((inString && current == '"') || (inChar && current == '\''))) {
                inString = false;
                inChar = false;
            }
            escaped = false;
            index++;
        }
        return restored.toString();
    }

    private UnicodeEscape unicodeEscapeAt(String source, int slashIndex) {
        int index = slashIndex + 1;
        if (index >= source.length() || source.charAt(index) != 'u') {
            return null;
        }
        while (index < source.length() && source.charAt(index) == 'u') {
            index++;
        }
        if (index + 4 > source.length()) {
            return null;
        }
        int codePoint = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(source.charAt(index + i), 16);
            if (digit < 0) {
                return null;
            }
            codePoint = (codePoint << 4) + digit;
        }
        return new UnicodeEscape(codePoint, index + 4);
    }

    private boolean isPrintableNonAscii(int codePoint) {
        if (codePoint <= 0x7E || codePoint == 0x7F || !Character.isValidCodePoint(codePoint)) {
            return false;
        }
        return !Character.isISOControl(codePoint)
                && codePoint != 0x2028
                && codePoint != 0x2029
                && (codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE);
    }

    private static class UnicodeEscape {
        private final int codePoint;
        private final int endIndex;

        private UnicodeEscape(int codePoint, int endIndex) {
            this.codePoint = codePoint;
            this.endIndex = endIndex;
        }
    }

    private String removeRepeatedValidationAnnotations(String source) {
        String processed = source;
        String previous;
        do {
            previous = processed;
            processed = processed.replaceAll(
                    "@Valid\\s+(@NotNull(?:\\([^)]*\\))?)\\s+@Valid\\s+\\1",
                    "@Valid $1");
            processed = processed.replaceAll("(@[A-Za-z_$][\\w$.]*(?:\\([^)]*\\))?)\\s+\\1", "$1");
        } while (!processed.equals(previous));
        return processed;
    }

    private String restoreLambdaQueryWrapperEntityTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> currentWrapperTypes = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("LambdaQueryWrapper") && line.contains("::")) {
                String entityType = firstMethodReferenceType(line);
                if (entityType != null) {
                    String typedWrapper = "LambdaQueryWrapper<" + entityType + ">";
                    Map<String, String> lineWrapperTypes = lambdaQueryWrapperVariables(line, entityType);
                    line = line.replace("new LambdaQueryWrapper()", "new " + typedWrapper + "()");
                    line = line.replace("(LambdaQueryWrapper)", "(" + typedWrapper + ")");
                    line = replaceLambdaQueryWrapperDeclarations(line, typedWrapper);
                    for (Map.Entry<String, String> entry : lineWrapperTypes.entrySet()) {
                        currentWrapperTypes.put(entry.getKey(), entry.getValue());
                        typeNearestPreviousLambdaQueryWrapperDeclaration(lines, i, entry.getKey(), entry.getValue());
                        typeNearestPreviousListDeclarationFromAssignment(lines, i, line, entry.getKey(), entry.getValue());
                    }
                }
            }
            line = typeListAssignmentLine(line, currentWrapperTypes);
            lines[i] = line;
        }

        return String.join("\n", lines);
    }

    private String restoreLambdaUpdateWrapperEntityTypes(String source) {
        String[] lines = source.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.contains("LambdaUpdateWrapper") || !line.contains("::")) {
                continue;
            }
            String entityType = firstMethodReferenceType(line, "LambdaUpdateWrapper");
            if (entityType == null) {
                continue;
            }
            String typedWrapper = "LambdaUpdateWrapper<" + entityType + ">";
            line = line.replace("new LambdaUpdateWrapper()", "new " + typedWrapper + "()");
            line = line.replace("(LambdaUpdateWrapper)", "(" + typedWrapper + ")");
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String restoreCfrLambdaMetafactoryMethodReferences(String source) {
        if (!source.contains("LambdaMetafactory.")) {
            return source;
        }
        Matcher matcher = CFR_LAMBDA_METAFACTORY_METHOD_REFERENCE.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String methodName = matcher.group(1);
            String ownerType = simpleInternalTypeName(matcher.group(2));
            if (!isGetterMethodReference(methodName) || ownerType == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(ownerType + "::" + methodName));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restoreSimpleThisMethodReferenceLambdas(String source) {
        Matcher matcher = SIMPLE_THIS_METHOD_REFERENCE_LAMBDA.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("." + matcher.group(1) + "(this::" + matcher.group(3) + ")"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restoreSwitchBraceSpacing(String source) {
        return SWITCH_OPENING_BRACE_WITHOUT_SPACE.matcher(source).replaceAll("$1 {");
    }

    private String restoreUnindentedMemberMethodDeclarations(String source) {
        String[] lines = source.split("\\n", -1);
        int braceDepth = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (braceDepth > 0 && !line.startsWith(" ") && !line.startsWith("\t")
                    && looksLikeMethodDeclaration(line)) {
                line = "    " + line;
            }
            lines[i] = line;
            braceDepth += countChar(line, '{') - countChar(line, '}');
            if (braceDepth < 0) {
                braceDepth = 0;
            }
        }
        return String.join("\n", lines);
    }

    private String restoreCfrBrokenBreakMarkers(String source) {
        return source.replaceAll("\\)\\s*\\*\\*\\s*break;", ") break;");
    }

    private String restoreCfrImplicitLocalDeclarations(String source) {
        String[] lines = source.split("\\n", -1);
        Set<String> declaredNames = new LinkedHashSet<>();
        Map<String, String> currentWrapperTypes = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (looksLikeMethodDeclaration(line)) {
                declaredNames.clear();
                currentWrapperTypes.clear();
                continue;
            }

            String restored = restoreImplicitStopWatchDeclaration(line, declaredNames);
            restored = restoreImplicitIntZeroDeclaration(restored, declaredNames);
            restored = restoreImplicitLambdaQueryWrapperDeclaration(restored, declaredNames, currentWrapperTypes);
            restored = restoreImplicitListDeclaration(restored, declaredNames, currentWrapperTypes);
            lines[i] = restored;
            collectDeclaredLocalNames(restored, declaredNames);
            collectLambdaQueryWrapperTypes(restored, currentWrapperTypes);
        }
        return String.join("\n", lines);
    }

    private String restoreImplicitStopWatchDeclaration(String line, Set<String> declaredNames) {
        Matcher matcher = Pattern.compile("^(\\s*)([A-Za-z_$][\\w$]*)\\s*=\\s*new\\s+StopWatch\\s*\\(")
                .matcher(line);
        if (!matcher.find() || declaredNames.contains(matcher.group(2))) {
            return line;
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(
                matcher.group(1) + "StopWatch " + matcher.group(2) + " = new StopWatch("));
    }

    private String restoreImplicitIntZeroDeclaration(String line, Set<String> declaredNames) {
        Matcher matcher = Pattern.compile("^(\\s*)([A-Za-z_$][\\w$]*)\\s*=\\s*0\\s*;").matcher(line);
        if (!matcher.find() || declaredNames.contains(matcher.group(2))
                || !isCommonCfrCounterLocal(matcher.group(2))) {
            return line;
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(
                matcher.group(1) + "int " + matcher.group(2) + " = 0;"));
    }

    private String restoreImplicitLambdaQueryWrapperDeclaration(String line, Set<String> declaredNames,
                                                               Map<String, String> currentWrapperTypes) {
        Matcher matcher = Pattern.compile("^(\\s*)([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*LambdaQueryWrapper\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>[^;\\n]*;)")
                .matcher(line);
        if (!matcher.find() || declaredNames.contains(matcher.group(2))) {
            return line;
        }
        String wrapperName = matcher.group(2);
        String entityType = matcher.group(4).trim();
        currentWrapperTypes.put(wrapperName, entityType);
        return matcher.replaceFirst(Matcher.quoteReplacement(
                matcher.group(1) + "LambdaQueryWrapper<" + entityType + "> " + wrapperName + " = "
                        + matcher.group(3)));
    }

    private String restoreImplicitListDeclaration(String line, Set<String> declaredNames,
                                                  Map<String, String> currentWrapperTypes) {
        Matcher matcher = Pattern.compile("^(\\s*)([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*\\.list\\s*\\(\\s*(?:\\(Wrapper\\)\\s*)?"
                + "([A-Za-z_$][\\w$]*)\\s*\\)[^;\\n]*;)")
                .matcher(line);
        if (!matcher.find() || declaredNames.contains(matcher.group(2))) {
            return line;
        }
        String entityType = currentWrapperTypes.get(matcher.group(4));
        if (entityType == null) {
            return line;
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(
                matcher.group(1) + "List<" + entityType + "> " + matcher.group(2) + " = " + matcher.group(3)));
    }

    private void collectDeclaredLocalNames(String line, Set<String> declaredNames) {
        Matcher matcher = Pattern.compile("\\b(?:StopWatch|int|LambdaQueryWrapper\\s*<[^>]+>|List(?:\\s*<[^>]+>)?)\\s+"
                + "([A-Za-z_$][\\w$]*)\\b").matcher(line);
        while (matcher.find()) {
            declaredNames.add(matcher.group(1));
        }
    }

    private void collectLambdaQueryWrapperTypes(String line, Map<String, String> currentWrapperTypes) {
        Matcher matcher = Pattern.compile("\\bLambdaQueryWrapper\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s+"
                + "([A-Za-z_$][\\w$]*)\\b").matcher(line);
        while (matcher.find()) {
            currentWrapperTypes.put(matcher.group(2), matcher.group(1));
        }
    }

    private boolean isCommonCfrCounterLocal(String name) {
        return "batch".equals(name) || "count".equals(name) || "num".equals(name);
    }

    private boolean isGetterMethodReference(String methodName) {
        return methodName != null && (methodName.startsWith("get") || methodName.startsWith("is"));
    }

    private String simpleInternalTypeName(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return null;
        }
        int lastSlash = internalName.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
        return simpleName.replace('$', '.');
    }

    private String firstMethodReferenceType(String line) {
        int wrapperStart = line.indexOf("new LambdaQueryWrapper");
        if (wrapperStart < 0) {
            wrapperStart = line.indexOf("(LambdaQueryWrapper");
        }
        String search = wrapperStart >= 0 ? line.substring(wrapperStart) : line;
        Matcher matcher = METHOD_REFERENCE_TYPE.matcher(search);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String firstMethodReferenceType(String line, String wrapperName) {
        int wrapperStart = line.indexOf("new " + wrapperName);
        if (wrapperStart < 0) {
            wrapperStart = line.indexOf("(" + wrapperName);
        }
        String search = wrapperStart >= 0 ? line.substring(wrapperStart) : line;
        Matcher matcher = METHOD_REFERENCE_TYPE.matcher(search);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private Map<String, String> lambdaQueryWrapperVariables(String line, String entityType) {
        Map<String, String> wrapperTypes = new LinkedHashMap<>();
        Matcher declaration = LAMBDA_QUERY_WRAPPER_DECLARATION.matcher(line);
        while (declaration.find()) {
            wrapperTypes.put(declaration.group(1), entityType);
        }
        Matcher assignment = LAMBDA_QUERY_WRAPPER_ASSIGNMENT.matcher(line);
        while (assignment.find()) {
            wrapperTypes.put(assignment.group(1), entityType);
        }
        Pattern receiverCall = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*\\.\\s*"
                + "[A-Za-z_$][\\w$]*\\s*\\(\\s*" + Pattern.quote(entityType) + "::");
        Matcher receiver = receiverCall.matcher(line);
        while (receiver.find()) {
            wrapperTypes.put(receiver.group(1), entityType);
        }
        return wrapperTypes;
    }

    private String replaceLambdaQueryWrapperDeclarations(String line, String typedWrapper) {
        Matcher matcher = LAMBDA_QUERY_WRAPPER_DECLARATION.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(typedWrapper + " " + matcher.group(1) + " ="));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void typeNearestPreviousLambdaQueryWrapperDeclaration(String[] lines, int currentIndex,
                                                                  String wrapperName, String entityType) {
        Pattern declaration = Pattern.compile(
                "\\bLambdaQueryWrapper\\s+" + Pattern.quote(wrapperName) + "\\s*([;=])");
        for (int i = currentIndex - 1; i >= 0; i--) {
            Matcher matcher = declaration.matcher(lines[i]);
            if (matcher.find()) {
                String typedWrapper = "LambdaQueryWrapper<" + entityType + ">";
                String suffix = matcher.group(1).equals(";") ? ";" : " =";
                String typedLine = matcher.replaceFirst(Matcher.quoteReplacement(
                        typedWrapper + " " + wrapperName + suffix));
                lines[i] = typedLine.replaceFirst(
                        "\\bnew\\s+LambdaQueryWrapper\\s*\\(\\s*\\)",
                        Matcher.quoteReplacement("new " + typedWrapper + "()"));
                return;
            }
            if (lines[i].contains("{") || lines[i].contains("}")) {
                return;
            }
        }
    }

    private void typeNearestPreviousListDeclarationFromAssignment(String[] lines, int currentIndex, String line,
                                                                  String wrapperName, String entityType) {
        Pattern assignment = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*=\\s*[^;\\n]*\\blist\\s*"
                + "\\(\\s*\\(Wrapper\\)\\s*\\(*\\s*"
                + Pattern.quote(wrapperName)
                + "\\b");
        Matcher assignmentMatcher = assignment.matcher(line);
        while (assignmentMatcher.find()) {
            String listName = assignmentMatcher.group(1);
            Pattern declaration = Pattern.compile("\\bList\\s+" + Pattern.quote(listName) + "\\s*;");
            for (int i = currentIndex - 1; i >= 0; i--) {
                Matcher declarationMatcher = declaration.matcher(lines[i]);
                if (declarationMatcher.find()) {
                    lines[i] = declarationMatcher.replaceFirst(
                            Matcher.quoteReplacement("List<" + entityType + "> " + listName + ";"));
                    break;
                }
                if (lines[i].contains("{") || lines[i].contains("}")) {
                    break;
                }
            }
        }
    }

    private String typeListAssignmentLine(String line, Map<String, String> currentWrapperTypes) {
        String processed = line;
        for (Map.Entry<String, String> entry : currentWrapperTypes.entrySet()) {
            Pattern listAssignment = Pattern.compile("^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                    + "([^;\\n]*\\blist\\s*\\(\\s*\\(Wrapper\\)\\s*"
                    + Pattern.quote(entry.getKey())
                    + "\\s*\\)[^;\\n]*;)");
            processed = typeListAssignment(processed, listAssignment, entry.getValue());
        }
        Pattern inlineAssignment = Pattern.compile("^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*new\\s+LambdaQueryWrapper<([A-Za-z_$][\\w$.]*)>\\(\\)[^;\\n]*;)");
        Matcher matcher = inlineAssignment.matcher(processed);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "List<" + matcher.group(4) + "> " + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String typeListAssignment(String line, Pattern listAssignment, String entityType) {
        Matcher matcher = listAssignment.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "List<" + entityType + "> " + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restoreServiceBatchListEntityTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Set<String> requiredImports = new LinkedHashSet<>();
        Pattern serviceListAssignment = Pattern.compile("^(\\s*)List(?:\\s*<\\s*[A-Za-z_$][\\w$.]*\\s*>)?\\s+"
                + "([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "((?:this\\.)?([A-Za-z_$][\\w$]*)\\.([A-Za-z_$][\\w$]*)\\s*\\([^;\\n]*;)");
        Pattern wrapperArgument = Pattern.compile("\\b(?:list|page)\\s*\\(\\s*\\(Wrapper\\)\\s*"
                + "([A-Za-z_$][\\w$]*)\\s*\\)");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = serviceListAssignment.matcher(lines[i]);
            if (!matcher.find()) {
                continue;
            }
            String listName = matcher.group(2);
            String serviceName = matcher.group(4);
            if (!isBatchUpdatedBySameService(lines, i + 1, serviceName, listName)) {
                continue;
            }
            String entityType = entityTypeFromServiceField(serviceName);
            if (entityType == null) {
                continue;
            }
            String entityImport = siblingDomainEntityImport(source, entityType);
            if (entityImport != null) {
                requiredImports.add(entityImport);
            }
            lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                    matcher.group(1) + "List<" + entityType + "> " + listName + " = " + matcher.group(3)));

            Matcher wrapperMatcher = wrapperArgument.matcher(lines[i]);
            if (wrapperMatcher.find()) {
                typeNearestPreviousLambdaQueryWrapperDeclaration(lines, i, wrapperMatcher.group(1), entityType);
            }
        }
        String processed = String.join("\n", lines);
        for (String requiredImport : requiredImports) {
            processed = ensureImport(processed, requiredImport);
        }
        return processed;
    }

    private boolean isBatchUpdatedBySameService(String[] lines, int start, String serviceName, String listName) {
        Pattern batchUpdate = Pattern.compile("\\b(?:this\\.)?" + Pattern.quote(serviceName)
                + "\\.(?:batchUpdateCryptById|batchUpdateById|updateBatchById)\\s*\\(\\s*"
                + Pattern.quote(listName) + "\\s*\\)");
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return false;
            }
            if (batchUpdate.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }

    private String entityTypeFromServiceField(String serviceName) {
        if (!serviceName.endsWith("Service") || serviceName.length() <= "Service".length()) {
            return null;
        }
        String stem = serviceName.substring(0, serviceName.length() - "Service".length());
        if (stem.isEmpty()) {
            return null;
        }
        if (stem.length() > 1 && stem.charAt(0) == 'i' && Character.isUpperCase(stem.charAt(1))) {
            stem = stem.substring(1);
        }
        if (stem.isEmpty()) {
            return null;
        }
        return Character.toUpperCase(stem.charAt(0)) + stem.substring(1);
    }

    private String siblingDomainEntityImport(String source, String entityType) {
        if (simpleImports(source).containsKey(entityType)) {
            return null;
        }
        Matcher matcher = IMPORT_DECLARATION.matcher(source);
        String directEntityPackage = null;
        while (matcher.find()) {
            String imported = matcher.group(1);
            int lastDot = imported.lastIndexOf('.');
            if (lastDot < 0) {
                continue;
            }
            String packageName = imported.substring(0, lastDot);
            if (packageName.endsWith(".domain.entity")) {
                directEntityPackage = packageName;
            }
        }
        return directEntityPackage == null ? null : directEntityPackage + "." + entityType;
    }

    private String restorePageInfoRowListElementTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Pattern rowListCast = Pattern.compile("\\(\\s*([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*\\)\\s*"
                + "([A-Za-z_$][\\w$]*)\\.getRowList\\(\\)");
        for (int i = 0; i < lines.length; i++) {
            Matcher methodReferenceMatcher = PAGE_INFO_ROW_LIST_METHOD_REFERENCE.matcher(lines[i]);
            while (methodReferenceMatcher.find()) {
                typeNearestPageInfoDeclaration(lines, i, methodReferenceMatcher.group(1),
                        methodReferenceMatcher.group(2));
            }
            Matcher castMatcher = rowListCast.matcher(lines[i]);
            while (castMatcher.find()) {
                if (isBroadCollectionCastType(castMatcher.group(1))) {
                    continue;
                }
                typeNearestPageInfoDeclaration(lines, i, castMatcher.group(2), castMatcher.group(1));
            }
        }
        return String.join("\n", lines);
    }

    private String restorePageInfoTypesFromMapstructRows(String source,
                                                         Map<String, Map<String, String>> mapstructInputTypes) {
        if (mapstructInputTypes == null || mapstructInputTypes.isEmpty()) {
            return source;
        }
        String[] lines = source.split("\\n", -1);
        Map<String, String> lambdaPageInfoNames = new LinkedHashMap<>();
        Set<String> requiredImports = new LinkedHashSet<>();
        Pattern rowListLambda = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\.getRowList\\(\\)\\.stream\\(\\)"
                + "\\.map\\(\\s*([A-Za-z_$][\\w$]*)\\s*->");
        for (int i = 0; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                lambdaPageInfoNames.clear();
            }
            Matcher rowMatcher = rowListLambda.matcher(lines[i]);
            while (rowMatcher.find()) {
                lambdaPageInfoNames.put(rowMatcher.group(2), rowMatcher.group(1));
            }
            for (Map.Entry<String, String> entry : lambdaPageInfoNames.entrySet()) {
                String elementType = mapstructInputTypeForLambda(lines[i], entry.getKey(), mapstructInputTypes);
                if (elementType != null) {
                    typeNearestPageInfoDeclaration(lines, i, entry.getValue(), simpleTypeName(elementType));
                    if (elementType.contains(".")) {
                        requiredImports.add(elementType);
                    }
                }
            }
            if (lines[i].contains("}).collect")) {
                lambdaPageInfoNames.clear();
            }
        }
        String processed = String.join("\n", lines);
        for (String requiredImport : requiredImports) {
            processed = ensureImport(processed, requiredImport);
        }
        return processed;
    }

    private String mapstructInputTypeForLambda(String line, String lambdaName,
                                               Map<String, Map<String, String>> mapstructInputTypes) {
        Pattern mapperCall = Pattern.compile("(?:\\(\\s*([A-Za-z_$][\\w$]*Mapstruct)\\s*\\)\\s*)?"
                + "([A-Za-z_$][\\w$]*Mapstruct)\\.INSTANCE\\)*\\.([A-Za-z_$][\\w$]*)\\s*\\(\\s*"
                + Pattern.quote(lambdaName) + "\\b");
        Matcher matcher = mapperCall.matcher(line);
        while (matcher.find()) {
            Map<String, String> methodTypes = mapstructInputTypes.get(matcher.group(2));
            if (methodTypes == null) {
                continue;
            }
            String inputType = methodTypes.get(matcher.group(3));
            if (inputType != null) {
                return inputType;
            }
        }
        return null;
    }

    private String removeRedundantPageInfoRowListElementCasts(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> pageInfoTypes = new LinkedHashMap<>();
        Pattern declaration = Pattern.compile("\\bPageInfo\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s+"
                + "([A-Za-z_$][\\w$]*)\\b");
        for (int i = 0; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                pageInfoTypes.clear();
            }
            Matcher declarationMatcher = declaration.matcher(lines[i]);
            while (declarationMatcher.find()) {
                pageInfoTypes.put(declarationMatcher.group(2), declarationMatcher.group(1));
            }
            String line = lines[i];
            for (Map.Entry<String, String> entry : pageInfoTypes.entrySet()) {
                line = removeRedundantPageInfoRowListElementCast(line, entry.getKey(), entry.getValue());
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String removeRedundantPageInfoRowListElementCast(String line, String pageInfoName, String elementType) {
        String simpleElementType = simpleTypeName(elementType);
        String typePattern = Pattern.quote(elementType);
        if (!simpleElementType.equals(elementType)) {
            typePattern = "(?:" + Pattern.quote(elementType) + "|" + Pattern.quote(simpleElementType) + ")";
        }
        Pattern castPattern = Pattern.compile("\\(\\(\\s*" + typePattern + "\\s*\\)\\s*"
                + Pattern.quote(pageInfoName)
                + "\\.getRowList\\(\\)\\.(getFirst\\(\\)|getLast\\(\\)|get\\([^()]*\\))\\)");
        Matcher matcher = castPattern.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    pageInfoName + ".getRowList()." + matcher.group(1)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restorePageInfoTypesFromLocalMapperMethods(String source) {
        Map<String, String> methodParameterTypes = localSingleArgumentMethodTypes(source);
        if (methodParameterTypes.isEmpty()) {
            return source;
        }
        String[] lines = source.split("\\n", -1);
        Pattern rowListLambda = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\.getRowList\\(\\)\\.stream\\(\\)"
                + "\\.map\\(\\s*([A-Za-z_$][\\w$]*)\\s*->\\s*this\\.([A-Za-z_$][\\w$]*)\\s*\\(\\s*\\2\\s*\\)");
        Pattern rowListMethodReference = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\.getRowList\\(\\)\\.stream\\(\\)"
                + "\\.map\\(\\s*this::([A-Za-z_$][\\w$]*)\\s*\\)");
        Set<String> requiredImports = new LinkedHashSet<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher lambdaMatcher = rowListLambda.matcher(lines[i]);
            while (lambdaMatcher.find()) {
                String elementType = methodParameterTypes.get(lambdaMatcher.group(3));
                if (elementType != null) {
                    typeNearestPageInfoDeclaration(lines, i, lambdaMatcher.group(1), simpleTypeName(elementType));
                    if (elementType.contains(".")) {
                        requiredImports.add(elementType);
                    }
                }
            }
            Matcher referenceMatcher = rowListMethodReference.matcher(lines[i]);
            while (referenceMatcher.find()) {
                String elementType = methodParameterTypes.get(referenceMatcher.group(2));
                if (elementType != null) {
                    typeNearestPageInfoDeclaration(lines, i, referenceMatcher.group(1), simpleTypeName(elementType));
                    if (elementType.contains(".")) {
                        requiredImports.add(elementType);
                    }
                }
            }
        }
        String processed = String.join("\n", lines);
        for (String requiredImport : requiredImports) {
            processed = ensureImport(processed, requiredImport);
        }
        return processed;
    }

    private Map<String, String> localSingleArgumentMethodTypes(String source) {
        Map<String, String> imports = simpleImports(source);
        Map<String, String> methodTypes = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("(?m)^\\s*(?:public|protected|private)\\s+"
                + "[A-Za-z_$][\\w$.]*(?:\\s*<[^;()]+>)?\\s+([A-Za-z_$][\\w$]*)\\s*\\(\\s*"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)?)\\s+[A-Za-z_$][\\w$]*\\s*\\)")
                .matcher(source);
        while (matcher.find()) {
            methodTypes.put(matcher.group(1), resolveImportedType(matcher.group(2), imports));
        }
        return methodTypes;
    }

    private String simpleTypeName(String typeName) {
        int lastDot = typeName.lastIndexOf('.');
        return lastDot >= 0 ? typeName.substring(lastDot + 1) : typeName;
    }

    private String ensureImport(String source, String fullyQualifiedClassName) {
        if (fullyQualifiedClassName == null || !fullyQualifiedClassName.contains(".")) {
            return source;
        }
        if (fullyQualifiedClassName.startsWith("java.lang.")
                && fullyQualifiedClassName.indexOf('.', "java.lang.".length()) < 0) {
            return source;
        }
        Matcher packageMatcher = PACKAGE_DECLARATION.matcher(source);
        String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        if (!packageName.isEmpty() && fullyQualifiedClassName.substring(0, lastDot).equals(packageName)) {
            return source;
        }
        String importLine = "import " + fullyQualifiedClassName + ";\n";
        if (source.contains(importLine) || source.contains("import " + fullyQualifiedClassName + ";\r\n")) {
            return source;
        }
        Matcher importMatcher = IMPORT_DECLARATION.matcher(source);
        int insertAt = -1;
        while (importMatcher.find()) {
            insertAt = importMatcher.end();
        }
        if (insertAt >= 0) {
            return source.substring(0, insertAt) + importLine + source.substring(insertAt);
        }
        if (packageMatcher.reset().find()) {
            insertAt = packageMatcher.end();
            return source.substring(0, insertAt) + "\n\n" + importLine + source.substring(insertAt);
        }
        return importLine + source;
    }

    private boolean isBroadCollectionCastType(String typeName) {
        return typeName.equals("Collection") || typeName.equals("java.util.Collection")
                || typeName.equals("List") || typeName.equals("java.util.List")
                || typeName.equals("Object") || typeName.equals("java.lang.Object");
    }

    private void typeNearestPageInfoDeclaration(String[] lines, int currentIndex, String pageInfoName,
                                                String elementType) {
        Pattern declaration = Pattern.compile("\\bPageInfo(?:\\s*<[^>]+>)?\\s+"
                + Pattern.quote(pageInfoName) + "\\s*([;=])");
        for (int i = currentIndex; i >= 0; i--) {
            if (i != currentIndex && looksLikeMethodDeclaration(lines[i])) {
                return;
            }
            Matcher matcher = declaration.matcher(lines[i]);
            if (matcher.find()) {
                String suffix = matcher.group(1).equals(";") ? ";" : " =";
                lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                        "PageInfo<" + elementType + "> " + pageInfoName + suffix));
                return;
            }
        }
    }

    private String restoreLambdaQueryChainWrapperEntityTypes(String source) {
        String[] lines = source.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.contains("LambdaQueryChainWrapper") || !line.contains("::")) {
                continue;
            }
            String entityType = firstLambdaQueryChainWrapperMethodReferenceType(line);
            if (entityType == null) {
                continue;
            }
            line = line.replace("(LambdaQueryChainWrapper)", "(LambdaQueryChainWrapper<" + entityType + ">)");
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String restoreLambdaUpdateChainWrapperEntityTypes(String source) {
        String[] lines = source.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.contains("LambdaUpdateChainWrapper") || !line.contains("::")) {
                continue;
            }
            String entityType = firstLambdaUpdateChainWrapperMethodReferenceType(line);
            if (entityType == null) {
                continue;
            }
            line = line.replace("(LambdaUpdateChainWrapper)", "(LambdaUpdateChainWrapper<" + entityType + ">)");
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String firstLambdaQueryChainWrapperMethodReferenceType(String line) {
        int wrapperStart = line.indexOf("(LambdaQueryChainWrapper");
        if (wrapperStart < 0) {
            wrapperStart = line.indexOf(".lambdaQuery()");
        }
        String search = wrapperStart >= 0 ? line.substring(wrapperStart) : line;
        Matcher matcher = METHOD_REFERENCE_TYPE.matcher(search);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String firstLambdaUpdateChainWrapperMethodReferenceType(String line) {
        int wrapperStart = line.indexOf("(LambdaUpdateChainWrapper");
        if (wrapperStart < 0) {
            wrapperStart = line.indexOf(".lambdaUpdate()");
        }
        String search = wrapperStart >= 0 ? line.substring(wrapperStart) : line;
        Matcher matcher = METHOD_REFERENCE_TYPE.matcher(search);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String restoreKnownRawGenericUtilityTypes(String source) {
        String processed = source;
        processed = processed.replaceAll("\\bList\\s*<\\s*FieldData\\s*>\\s+"
                        + "([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]*\\.scanList\\([^;\\n]*;)",
                "List<FieldData<Object>> $1 = $2");
        processed = processed.replaceAll("\\bList\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                        + "([A-Za-z_$][\\w$]*)\\.stream\\(\\)\\.map\\(FieldData::getField\\)"
                        + "\\.collect\\(Collectors\\.toList\\(\\)\\);",
                "List<Field> $1 = $2.stream().map(FieldData::getField).collect(Collectors.toList());");
        processed = processed.replaceAll("\\bArrayList\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                        + "Lists\\.newArrayList\\(\\s*new\\s+Class\\[\\]\\s*\\{\\s*([^}]*)\\s*}\\s*\\);",
                "ArrayList<Class<? extends Annotation>> $1 = Lists.newArrayList($2);");
        processed = processed.replaceAll("\\b(List\\s*<\\s*Class\\s*<\\s*\\?\\s*extends\\s*Annotation\\s*>\\s*>\\s+"
                        + "[A-Za-z_$][\\w$]*\\s*=\\s*)Lists\\.newArrayList\\(\\s*new\\s+Class\\[\\]\\s*"
                        + "\\{\\s*([^}]*)\\s*}\\s*\\);",
                "$1Arrays.asList($2);");
        processed = restoreTimeSplitterPairTypes(processed);
        if (processed.contains("List<Field> ")) {
            processed = ensureImport(processed, "java.lang.reflect.Field");
        }
        if (processed.contains("Class<? extends Annotation>")) {
            processed = ensureImport(processed, "java.lang.annotation.Annotation");
        }
        if (processed.contains("Arrays.asList(")) {
            processed = ensureImport(processed, "java.util.Arrays");
        }
        return processed;
    }

    private String restoreTimeSplitterPairTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Set<String> pairListNames = new LinkedHashSet<>();
        Pattern assignment = Pattern.compile("\\bList\\s*<\\s*Pair\\s*>\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*TimeSplitter\\.splitTimeRange\\([^;\\n]*;)");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = assignment.matcher(lines[i]);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                pairListNames.add(matcher.group(1));
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        "List<Pair<LocalDateTime, LocalDateTime>> " + matcher.group(1) + " = " + matcher.group(2)));
            }
            matcher.appendTail(buffer);
            lines[i] = buffer.toString();
            for (String pairListName : pairListNames) {
                lines[i] = lines[i].replaceAll("\\bfor\\s*\\(\\s*Pair\\s+([A-Za-z_$][\\w$]*)\\s*:\\s*"
                                + Pattern.quote(pairListName) + "\\s*\\)",
                        "for (Pair<LocalDateTime, LocalDateTime> $1 : " + pairListName + ")");
            }
            if (looksLikeMethodDeclaration(lines[i])) {
                pairListNames.clear();
            }
        }
        return String.join("\n", lines);
    }

    private String restoreRawListLongValueOfStreams(String source) {
        return source.replaceAll("\\(\\(List\\)([^)]+)\\)\\.stream\\(\\)\\.map\\(Long::valueOf\\)",
                "((List<?>)$1).stream().map(String::valueOf).map(Long::valueOf)");
    }

    private String restoreBooleanHandleResultTernaries(String source) {
        String[] lines = source.split("\\n", -1);
        boolean inBooleanResultMethod = false;
        Pattern booleanResultMethod = Pattern.compile("\\bResult\\s*<\\s*Boolean\\s*>\\s+[A-Za-z_$][\\w$]*\\s*\\(");
        Pattern ternaryHandleResult = Pattern.compile("return\\s+this\\.handleResult\\(\\(([^?;]+?)\\s*\\?\\s*1\\s*:\\s*0\\)\\);");
        for (int i = 0; i < lines.length; i++) {
            if (booleanResultMethod.matcher(lines[i]).find()) {
                inBooleanResultMethod = true;
            } else if (looksLikeMethodDeclaration(lines[i])) {
                inBooleanResultMethod = false;
            }
            if (!inBooleanResultMethod) {
                continue;
            }
            Matcher matcher = ternaryHandleResult.matcher(lines[i]);
            if (matcher.find()) {
                lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                        "return this.handleResult(" + matcher.group(1).trim() + ");"));
            }
        }
        return String.join("\n", lines);
    }

    private String restorePageDataGetListLocalTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> pageDataTypes = new LinkedHashMap<>();
        Pattern pageDataDeclaration = Pattern.compile("\\bPageData\\s*<\\s*([A-Za-z_$][\\w$.]*)\\s*>\\s+"
                + "([A-Za-z_$][\\w$]*)\\s*=");
        for (int i = 0; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                pageDataTypes.clear();
            }
            Matcher pageDataMatcher = pageDataDeclaration.matcher(lines[i]);
            while (pageDataMatcher.find()) {
                pageDataTypes.put(pageDataMatcher.group(2), pageDataMatcher.group(1));
            }
            for (Map.Entry<String, String> entry : pageDataTypes.entrySet()) {
                Pattern listFromPageData = Pattern.compile("\\bList(?:\\s*<[^>]+>)?\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                        + Pattern.quote(entry.getKey()) + "\\.getList\\(\\);");
                Matcher listMatcher = listFromPageData.matcher(lines[i]);
                if (listMatcher.find()) {
                    lines[i] = listMatcher.replaceFirst(Matcher.quoteReplacement(
                            "List<" + entry.getValue() + "> " + listMatcher.group(1)
                                    + " = " + entry.getKey() + ".getList();"));
                }
            }
        }
        return String.join("\n", lines);
    }

    private String restorePageDataReturnConstructors(String source) {
        String[] lines = source.split("\\n", -1);
        boolean inPageDataMethod = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (PAGE_DATA_METHOD_DECLARATION.matcher(line).find()) {
                inPageDataMethod = true;
            } else if (looksLikeMethodDeclaration(line)) {
                inPageDataMethod = false;
            }
            if (inPageDataMethod && line.contains("new PageData(")) {
                line = line.replaceAll("\\(long\\)\\s*([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*\\([^()]*\\))*"
                        + "\\.(?:intValue|getPages)\\(\\))", "$1");
                line = restorePageDataConstructorDiamond(line);
                lines[i] = line;
            }
        }
        return String.join("\n", lines);
    }

    private String restorePageDataConstructorDiamond(String line) {
        StringBuilder builder = new StringBuilder(line.length() + 8);
        int searchIndex = 0;
        while (true) {
            int constructorIndex = line.indexOf("new PageData(", searchIndex);
            if (constructorIndex < 0) {
                builder.append(line, searchIndex, line.length());
                return builder.toString();
            }

            int openParen = constructorIndex + "new PageData".length();
            int closeParen = findMatchingParen(line, openParen);
            if (closeParen < 0) {
                builder.append(line, searchIndex, constructorIndex + 1);
                searchIndex = constructorIndex + 1;
                continue;
            }

            String arguments = line.substring(openParen + 1, closeParen);
            builder.append(line, searchIndex, constructorIndex);
            if (countTopLevelArguments(arguments) <= 5) {
                builder.append("new PageData<>(").append(arguments).append(')');
            } else {
                builder.append(line, constructorIndex, closeParen + 1);
            }
            searchIndex = closeParen + 1;
        }
    }

    private int countTopLevelArguments(String arguments) {
        return splitTopLevelArguments(arguments).size();
    }

    private List<String> splitTopLevelArguments(String arguments) {
        List<String> result = new ArrayList<>();
        if (arguments == null || arguments.trim().isEmpty()) {
            return result;
        }
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        int angleDepth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;
        int argumentStart = 0;
        for (int i = 0; i < arguments.length(); i++) {
            char current = arguments.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '\'') {
                inChar = true;
            } else if (current == '(') {
                parenDepth++;
            } else if (current == ')' && parenDepth > 0) {
                parenDepth--;
            } else if (current == '[') {
                bracketDepth++;
            } else if (current == ']' && bracketDepth > 0) {
                bracketDepth--;
            } else if (current == '{') {
                braceDepth++;
            } else if (current == '}' && braceDepth > 0) {
                braceDepth--;
            } else if (current == '<') {
                angleDepth++;
            } else if (current == '>' && angleDepth > 0) {
                angleDepth--;
            } else if (current == ',' && parenDepth == 0 && bracketDepth == 0
                    && braceDepth == 0 && angleDepth == 0) {
                result.add(arguments.substring(argumentStart, i).trim());
                argumentStart = i + 1;
            }
        }
        result.add(arguments.substring(argumentStart).trim());
        return result;
    }

    private String restoreCheckedExceptionHandlers(String source) {
        String processed = wrapReflectiveGetCountHelper(source);
        processed = wrapClassForNameHelper(processed);
        processed = catchByteArrayOutputStreamResourceClose(processed);
        return wrapWorkbookCellLogicHelper(processed);
    }

    private String wrapReflectiveGetCountHelper(String source) {
        if (!source.contains("public static long getCount(") || source.contains("获取导出总数失败")) {
            return source;
        }
        String[] lines = source.split("\\n", -1);
        boolean inGetCount = false;
        boolean insertedTry = false;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("public static long getCount(")) {
                inGetCount = true;
                insertedTry = false;
                continue;
            }
            if (!inGetCount) {
                continue;
            }
            if (!insertedTry && lines[i].contains("Object result = null;")) {
                String indent = leadingWhitespace(lines[i]);
                lines[i] = indent + "try {\n" + indent + "    " + lines[i].trim();
                insertedTry = true;
                continue;
            }
            if (insertedTry && lines[i].trim().equals("return 0L;")) {
                String indent = leadingWhitespace(lines[i]);
                lines[i] = indent + "}\n"
                        + indent + "catch (Exception e) {\n"
                        + indent + "    log.error(\"获取导出总数失败 -> {}\", ExceptionUtil.stacktraceToString(e));\n"
                        + indent + "    throw new BusinessException(\"获取导出总数失败\");\n"
                        + indent + "}\n"
                        + lines[i];
                inGetCount = false;
                continue;
            }
            if (insertedTry && !lines[i].trim().isEmpty()) {
                lines[i] = leadingWhitespace(lines[i]) + "    " + lines[i].trim();
            }
        }
        return String.join("\n", lines);
    }

    private String wrapClassForNameHelper(String source) {
        if (!source.contains("private static Class<?> getClazz(String returnType)")
                || source.contains("catch (ClassNotFoundException e)")) {
            return source;
        }
        String[] lines = source.split("\\n", -1);
        boolean inGetClazz = false;
        String methodIndent = "";
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("private static Class<?> getClazz(String returnType)")) {
                inGetClazz = true;
                methodIndent = leadingWhitespace(lines[i]);
                continue;
            }
            if (!inGetClazz) {
                continue;
            }
            if (lines[i].contains("return Class.forName(returnType);")) {
                String indent = leadingWhitespace(lines[i]);
                lines[i] = indent + "try {\n" + indent + "    return Class.forName(returnType);";
                continue;
            }
            if (lines[i].equals(methodIndent + "}")) {
                String indent = methodIndent + "    ";
                lines[i] = indent + "}\n"
                        + indent + "catch (ClassNotFoundException e) {\n"
                        + indent + "    throw new RuntimeException(e);\n"
                        + indent + "}\n"
                        + lines[i];
                break;
            }
        }
        return String.join("\n", lines);
    }

    private String catchByteArrayOutputStreamResourceClose(String source) {
        if (!source.contains("try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();)")
                && !source.contains("try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();){")) {
            return source;
        }
        if (source.contains("throw new RuntimeException(e);\n        }\n        return byteArrayInputStream;")) {
            return source;
        }
        return source.replaceFirst("\\n(\\s*)return byteArrayInputStream;",
                "\n$1catch (Exception e) {\n"
                        + "$1    throw new RuntimeException(e);\n"
                        + "$1}\n"
                        + "$1return byteArrayInputStream;");
    }

    private String wrapWorkbookCellLogicHelper(String source) {
        if (!source.contains("InputStream cellLogic(") || !source.contains("new XSSFWorkbook(")) {
            return source;
        }
        String[] lines = source.split("\\n", -1);
        boolean inCellLogic = false;
        boolean insertedTry = false;
        boolean returnSeen = false;
        String methodIndent = "";
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("InputStream cellLogic(")) {
                inCellLogic = true;
                insertedTry = false;
                returnSeen = false;
                methodIndent = leadingWhitespace(lines[i]);
                continue;
            }
            if (!inCellLogic) {
                continue;
            }
            if (!insertedTry && lines[i].contains("XSSFWorkbook workbook = new XSSFWorkbook(")) {
                String indent = leadingWhitespace(lines[i]);
                lines[i] = indent + "try {\n" + indent + "    " + lines[i].trim();
                insertedTry = true;
                continue;
            }
            if (insertedTry && lines[i].contains("return new ByteArrayInputStream(")) {
                returnSeen = true;
                continue;
            }
            if (insertedTry && returnSeen && lines[i].equals(methodIndent + "}")) {
                String indent = methodIndent + "    ";
                lines[i] = indent + "}\n"
                        + indent + "catch (Exception e) {\n"
                        + indent + "    throw new RuntimeException(e);\n"
                        + indent + "}\n"
                        + lines[i];
                break;
            }
        }
        return String.join("\n", lines);
    }

    private String leadingWhitespace(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return line.substring(0, index);
    }

    private String castGenericReflectionFieldAccess(String source) {
        if (!source.contains("Function<T, K> createFieldFunction")) {
            return source;
        }
        return source.replace("return field.get(obj);", "return (K)field.get(obj);");
    }

    private String restoreLambdaQueryChainListElementTypes(String source) {
        Matcher matcher = Pattern.compile("(?m)^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*LambdaQueryChainWrapper<([A-Za-z_$][\\w$.]*)>[^;\\n]*\\.list\\(\\)[^;\\n]*;)")
                .matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "List<" + matcher.group(4) + "> "
                            + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restorePageDataLocalsFromResultReturnTypes(String source) {
        String[] lines = source.split("\\n", -1);
        String currentElementType = null;
        Map<String, String> pageDataLocals = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher methodMatcher = PAGE_DATA_METHOD_DECLARATION.matcher(line);
            if (methodMatcher.find()) {
                currentElementType = methodMatcher.group(1);
                pageDataLocals.clear();
            } else if (looksLikeMethodDeclaration(line)) {
                currentElementType = null;
                pageDataLocals.clear();
            }
            if (currentElementType == null) {
                continue;
            }

            Matcher pageDataMatcher = Pattern.compile("\\bPageData\\s+([A-Za-z_$][\\w$]*)\\s*([;=])")
                    .matcher(line);
            StringBuffer pageDataBuffer = new StringBuffer();
            while (pageDataMatcher.find()) {
                String localName = pageDataMatcher.group(1);
                if (isPageDataListMappedByMapstruct(source, localName)) {
                    pageDataMatcher.appendReplacement(pageDataBuffer, Matcher.quoteReplacement(pageDataMatcher.group()));
                    continue;
                }
                pageDataLocals.put(localName, currentElementType);
                String suffix = pageDataMatcher.group(2).equals(";") ? ";" : " =";
                pageDataMatcher.appendReplacement(pageDataBuffer, Matcher.quoteReplacement(
                        "PageData<" + currentElementType + "> " + localName + suffix));
            }
            pageDataMatcher.appendTail(pageDataBuffer);
            line = pageDataBuffer.toString();

            for (Map.Entry<String, String> entry : pageDataLocals.entrySet()) {
                Pattern listFromPageData = Pattern.compile("^(\\s*)List\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                        + Pattern.quote(entry.getKey())
                        + "\\.getList\\(\\);");
                Matcher listMatcher = listFromPageData.matcher(line);
                if (listMatcher.find()) {
                    line = listMatcher.replaceFirst(Matcher.quoteReplacement(
                            listMatcher.group(1) + "List<" + entry.getValue() + "> "
                                    + listMatcher.group(2) + " = " + entry.getKey() + ".getList();"));
                }
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private boolean looksLikeMethodDeclaration(String line) {
        return line.matches("\\s*(?:public|protected|private)\\s+[^=;{}]+\\([^;{}]*\\)\\s*"
                + "(?:throws\\s+[^;{}]+)?\\s*\\{?\\s*");
    }

    private boolean isPageDataListMappedByMapstruct(String source, String pageDataName) {
        Pattern mappedList = Pattern.compile("\\b[A-Za-z_$][\\w$]*Mapstruct\\.INSTANCE\\.toList\\s*"
                + "\\(\\s*" + Pattern.quote(pageDataName) + "\\.getList\\(\\)\\s*\\)");
        return mappedList.matcher(source).find();
    }

    private String restorePageInfoLocalsFromPageDataReturnTypes(String source) {
        String[] lines = source.split("\\n", -1);
        String currentElementType = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher methodMatcher = PAGE_DATA_METHOD_DECLARATION.matcher(line);
            if (methodMatcher.find()) {
                currentElementType = methodMatcher.group(1);
            } else if (looksLikeMethodDeclaration(line)) {
                currentElementType = null;
            }
            if (currentElementType != null) {
                Matcher pageInfoMatcher = Pattern.compile("\\bPageInfo\\s+([A-Za-z_$][\\w$]*)\\s*([;=])")
                        .matcher(line);
                StringBuffer buffer = new StringBuffer();
                while (pageInfoMatcher.find()) {
                    String localName = pageInfoMatcher.group(1);
                    if (isPageInfoRowsTransformed(source, localName)) {
                        pageInfoMatcher.appendReplacement(buffer, Matcher.quoteReplacement(pageInfoMatcher.group()));
                        continue;
                    }
                    String suffix = pageInfoMatcher.group(2).equals(";") ? ";" : " =";
                    pageInfoMatcher.appendReplacement(buffer, Matcher.quoteReplacement(
                            "PageInfo<" + currentElementType + "> " + localName + suffix));
                }
                pageInfoMatcher.appendTail(buffer);
                line = buffer.toString();
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private boolean isPageInfoRowsTransformed(String source, String pageInfoName) {
        Pattern mappedRows = Pattern.compile("\\b[A-Za-z_$][\\w$]*Mapstruct\\.INSTANCE\\.[A-Za-z_$][\\w$]*\\s*"
                + "\\(\\s*" + Pattern.quote(pageInfoName) + "\\.getRowList\\(\\)\\s*\\)");
        Pattern streamedRows = Pattern.compile("\\b" + Pattern.quote(pageInfoName)
                + "\\.getRowList\\(\\)\\.stream\\(\\)\\.map\\s*\\(");
        return mappedRows.matcher(source).find() || streamedRows.matcher(source).find();
    }

    private String restoreListLocalsFromListReturnTypes(String source) {
        String[] lines = source.split("\\n", -1);
        String currentElementType = null;
        int methodStart = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher methodMatcher = LIST_METHOD_DECLARATION.matcher(line);
            if (methodMatcher.find()) {
                currentElementType = methodMatcher.group(1);
                methodStart = i;
            } else {
                Matcher wrappedMethodMatcher = WRAPPED_LIST_METHOD_DECLARATION.matcher(line);
                if (wrappedMethodMatcher.find()) {
                    currentElementType = wrappedMethodMatcher.group(1);
                    methodStart = i;
                } else if (looksLikeMethodDeclaration(line)) {
                    Matcher pageDataMethodMatcher = PAGE_DATA_METHOD_DECLARATION.matcher(line);
                    if (pageDataMethodMatcher.find()) {
                        currentElementType = pageDataMethodMatcher.group(1);
                        methodStart = i;
                    } else {
                        currentElementType = null;
                        methodStart = -1;
                    }
                }
            }
            if (currentElementType == null || methodStart < 0) {
                continue;
            }
            Matcher listMatcher = Pattern.compile("\\bList\\s+([A-Za-z_$][\\w$]*)\\s*=")
                    .matcher(line);
            if (listMatcher.find() && isListReturnedBeforeNextMethod(lines, i + 1, listMatcher.group(1))) {
                lines[i] = listMatcher.replaceFirst(Matcher.quoteReplacement(
                        "List<" + currentElementType + "> " + listMatcher.group(1) + " ="));
            }
        }
        return String.join("\n", lines);
    }

    private String restoreListTypesFromStreamMapResults(String source) {
        String[] lines = source.split("\\n", -1);
        Pattern sameLineList = Pattern.compile("^(\\s*)List(?:\\s*<\\s*Object\\s*>)?\\s+"
                + "([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]*\\.stream\\s*\\([^;\\n]*\\.map\\(\\s*"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::([A-Za-z_$][\\w$]*)[^;\\n]*;)");
        Pattern assignment = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "[^;\\n]*\\.stream\\s*\\([^;\\n]*\\.map\\(\\s*"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::([A-Za-z_$][\\w$]*)");
        for (int i = 0; i < lines.length; i++) {
            Matcher sameLineMatcher = sameLineList.matcher(lines[i]);
            if (sameLineMatcher.find()) {
                String elementType = inferStreamMapResultType(sameLineMatcher.group(4), sameLineMatcher.group(5));
                if (elementType != null) {
                    lines[i] = sameLineMatcher.replaceFirst(Matcher.quoteReplacement(
                            sameLineMatcher.group(1) + "List<" + elementType + "> "
                                    + sameLineMatcher.group(2) + " = " + sameLineMatcher.group(3)));
                }
            }

            Matcher assignmentMatcher = assignment.matcher(lines[i]);
            while (assignmentMatcher.find()) {
                String elementType = inferStreamMapResultType(assignmentMatcher.group(2), assignmentMatcher.group(3));
                if (elementType != null) {
                    typePreviousListObjectDeclaration(lines, i, assignmentMatcher.group(1), elementType);
                }
            }
        }
        return String.join("\n", lines);
    }

    private String restoreSetTypesFromStreamMapResults(String source) {
        String[] lines = source.split("\\n", -1);
        Pattern sameLineSet = Pattern.compile("^(\\s*)Set\\s*<\\s*Object\\s*>\\s+"
                + "([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]*\\.stream\\s*\\([^;\\n]*\\.map\\(\\s*"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::([A-Za-z_$][\\w$]*)"
                + "[^;\\n]*\\.collect\\(\\s*Collectors\\.toSet\\(\\)\\s*\\)\\s*;)");
        Pattern assignment = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "[^;\\n]*\\.stream\\s*\\([^;\\n]*\\.map\\(\\s*"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::([A-Za-z_$][\\w$]*)"
                + "[^;\\n]*\\.collect\\(\\s*Collectors\\.toSet\\(\\)\\s*\\)");
        for (int i = 0; i < lines.length; i++) {
            Matcher sameLineMatcher = sameLineSet.matcher(lines[i]);
            if (sameLineMatcher.find()) {
                String elementType = inferStreamMapResultType(sameLineMatcher.group(4), sameLineMatcher.group(5));
                if (elementType != null) {
                    lines[i] = sameLineMatcher.replaceFirst(Matcher.quoteReplacement(
                            sameLineMatcher.group(1) + "Set<" + elementType + "> "
                                    + sameLineMatcher.group(2) + " = " + sameLineMatcher.group(3)));
                }
            }

            Matcher assignmentMatcher = assignment.matcher(lines[i]);
            while (assignmentMatcher.find()) {
                String elementType = inferStreamMapResultType(assignmentMatcher.group(2), assignmentMatcher.group(3));
                if (elementType != null) {
                    typePreviousSetDeclaration(lines, i, assignmentMatcher.group(1), elementType);
                }
            }
        }
        return String.join("\n", lines);
    }

    private String restoreStreamCollectListTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> listElementTypes = new LinkedHashMap<>();
        Pattern collectedFromTypedList = Pattern.compile("^(\\s*)List(?:\\s*<[^>]+>)?\\s+"
                + "([A-Za-z_$][\\w$]*)\\s*=\\s*([A-Za-z_$][\\w$]*)\\.stream\\(\\)"
                + "(?!\\.map\\()[^;\\n]*\\.collect\\(Collectors\\.toList\\(\\)\\);");
        Pattern mappedNewType = Pattern.compile("^(\\s*)List(?:\\s*<[^>]+>)?\\s+"
                + "([A-Za-z_$][\\w$]*)\\s*=\\s*([^;\\n]*\\.stream\\(\\)[^;\\n]*\\.map\\([^;\\n]*->\\s*new\\s+"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*\\([^;\\n]*;)");
        for (int i = 0; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                listElementTypes.clear();
            }
            Matcher declarationMatcher = TYPED_LIST_DECLARATION.matcher(lines[i]);
            while (declarationMatcher.find()) {
                listElementTypes.put(declarationMatcher.group(2), declarationMatcher.group(1));
            }

            Matcher newTypeMatcher = mappedNewType.matcher(lines[i]);
            if (newTypeMatcher.find()) {
                lines[i] = newTypeMatcher.replaceFirst(Matcher.quoteReplacement(
                        newTypeMatcher.group(1) + "List<" + newTypeMatcher.group(4) + "> "
                                + newTypeMatcher.group(2) + " = " + newTypeMatcher.group(3)));
                continue;
            }

            Matcher collectMatcher = collectedFromTypedList.matcher(lines[i]);
            if (!lines[i].contains(".map(") && collectMatcher.find()) {
                String elementType = listElementTypes.get(collectMatcher.group(3));
                if (elementType != null) {
                    lines[i] = collectMatcher.replaceFirst(Matcher.quoteReplacement(
                            collectMatcher.group(1) + "List<" + elementType + "> "
                                    + collectMatcher.group(2) + " = " + collectMatcher.group(3)
                                    + ".stream()" + lines[i].substring(collectMatcher.end(3) + ".stream()".length())));
                }
            }
        }
        return String.join("\n", lines);
    }

    private String restoreUidSideEffectListTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Pattern uidListArgument = Pattern.compile("\\.map\\(\\s*[A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*"
                + "::getUid\\)[^;\\n]*?,\\s*([A-Za-z_$][\\w$]*)\\s*(?:,|\\))");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = uidListArgument.matcher(lines[i]);
            while (matcher.find()) {
                typePreviousRawArrayListDeclaration(lines, i, matcher.group(1), "Long");
            }
        }
        return String.join("\n", lines);
    }

    private void typePreviousRawArrayListDeclaration(String[] lines, int currentIndex, String listName,
                                                     String elementType) {
        Pattern declaration = Pattern.compile("\\bArrayList\\s+" + Pattern.quote(listName)
                + "\\s*=\\s*new\\s+ArrayList\\s*\\(\\s*\\)\\s*;");
        for (int i = currentIndex; i >= 0; i--) {
            if (i != currentIndex && looksLikeMethodDeclaration(lines[i])) {
                return;
            }
            Matcher matcher = declaration.matcher(lines[i]);
            if (matcher.find()) {
                lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                        "List<" + elementType + "> " + listName + " = new ArrayList<>();"));
                return;
            }
        }
    }

    private String inferStreamMapResultType(String ownerType, String methodName) {
        if (ownerType.equals("Long") || ownerType.equals("Integer") || ownerType.equals("String")
                || ownerType.equals("Double") || ownerType.equals("Float") || ownerType.equals("BigDecimal")) {
            return ownerType;
        }
        return inferGetterReturnType(methodName);
    }

    private void typePreviousListObjectDeclaration(String[] lines, int currentIndex, String listName,
                                                   String elementType) {
        Pattern declaration = Pattern.compile("\\bList\\s*<\\s*Object\\s*>\\s+"
                + Pattern.quote(listName) + "\\s*([;=])");
        for (int i = currentIndex; i >= 0; i--) {
            if (i != currentIndex && looksLikeMethodDeclaration(lines[i])) {
                return;
            }
            Matcher matcher = declaration.matcher(lines[i]);
            if (matcher.find()) {
                String suffix = matcher.group(1).equals(";") ? ";" : " =";
                lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                        "List<" + elementType + "> " + listName + suffix));
                return;
            }
        }
    }

    private void typePreviousSetDeclaration(String[] lines, int currentIndex, String setName, String elementType) {
        Pattern declaration = Pattern.compile("\\bSet\\s*<\\s*Object\\s*>\\s+"
                + Pattern.quote(setName) + "\\s*([;=])");
        for (int i = currentIndex; i >= 0; i--) {
            if (i != currentIndex && looksLikeMethodDeclaration(lines[i])) {
                return;
            }
            Matcher matcher = declaration.matcher(lines[i]);
            if (matcher.find()) {
                String suffix = matcher.group(1).equals(";") ? ";" : " =";
                lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                        "Set<" + elementType + "> " + setName + suffix));
                return;
            }
        }
    }

    private boolean isListReturnedBeforeNextMethod(String[] lines, int start, String localName) {
        return isReturnedBeforeNextMethod(lines, start, localName)
                || isPassedToReturnCallBeforeNextMethod(lines, start, localName);
    }

    private boolean isReturnedBeforeNextMethod(String[] lines, int start, String localName) {
        Pattern returned = Pattern.compile("\\breturn\\s+" + Pattern.quote(localName) + "\\s*;");
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return false;
            }
            if (returned.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean isPassedToReturnCallBeforeNextMethod(String[] lines, int start, String localName) {
        Pattern returned = Pattern.compile("\\breturn\\s+[^;\\n]+\\(\\s*" + Pattern.quote(localName)
                + "\\s*(?:,|\\))");
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return false;
            }
            if (returned.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }

    private String restoreNestedListPartitionTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> listElementTypes = new LinkedHashMap<>();
        Map<String, String> partitionElementTypes = new LinkedHashMap<>();
        Pattern partitionAssignment = Pattern.compile("^(\\s*)List(?:\\s*<\\s*List\\s*>)?\\s+"
                + "([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "((?:com\\.google\\.common\\.collect\\.)?Lists\\.partition\\(\\s*)"
                + "(?:\\(List\\)\\s*)?([A-Za-z_$][\\w$]*)\\s*,\\s*(?:\\(int\\)\\s*)?([^;\\n]+)\\);");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (looksLikeMethodDeclaration(line)) {
                listElementTypes.clear();
                partitionElementTypes.clear();
            }

            Matcher typedListMatcher = TYPED_LIST_DECLARATION.matcher(line);
            while (typedListMatcher.find()) {
                listElementTypes.put(typedListMatcher.group(2), typedListMatcher.group(1));
            }

            Matcher partitionMatcher = partitionAssignment.matcher(line);
            if (partitionMatcher.find()) {
                String partitionName = partitionMatcher.group(2);
                String sourceListName = partitionMatcher.group(4);
                String elementType = listElementTypes.get(sourceListName);
                if (elementType != null) {
                    partitionElementTypes.put(partitionName, elementType);
                    line = partitionMatcher.replaceFirst(Matcher.quoteReplacement(
                            partitionMatcher.group(1) + "List<List<" + elementType + ">> " + partitionName + " = "
                                    + partitionMatcher.group(3) + sourceListName + ", "
                                    + partitionMatcher.group(5).trim() + ");"));
                }
            }

            for (Map.Entry<String, String> entry : partitionElementTypes.entrySet()) {
                line = typeNestedListEnhancedFor(line, entry.getKey(), entry.getValue());
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String restoreRawListTypesFromEnhancedForUsage(String source) {
        String[] lines = source.split("\\n", -1);
        Pattern enhancedFor = Pattern.compile("\\bfor\\s*\\(\\s*([^:\\n]+?)\\s+[A-Za-z_$][\\w$]*\\s*:\\s*"
                + "([A-Za-z_$][\\w$]*)\\s*\\)");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = enhancedFor.matcher(lines[i]);
            while (matcher.find()) {
                String elementType = matcher.group(1).trim();
                String listName = matcher.group(2);
                typePreviousRawListDeclaration(lines, i, listName, elementType);
            }
        }
        return String.join("\n", lines);
    }

    private String restoreRawListTypesFromElementUsage(String source) {
        String[] lines = source.split("\\n", -1);
        Pattern castGet = Pattern.compile("\\(\\s*([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*\\)\\s*"
                + "([A-Za-z_$][\\w$]*)\\.get(?:First|Last)?\\s*\\(");
        Pattern streamMap = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\.stream\\(\\)\\.map\\(\\s*"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::");
        for (int i = 0; i < lines.length; i++) {
            Matcher castMatcher = castGet.matcher(lines[i]);
            while (castMatcher.find()) {
                typePreviousRawListDeclaration(lines, i, castMatcher.group(2), castMatcher.group(1));
            }
            Matcher streamMatcher = streamMap.matcher(lines[i]);
            while (streamMatcher.find()) {
                typePreviousRawListDeclaration(lines, i, streamMatcher.group(1), streamMatcher.group(2));
            }
        }
        return String.join("\n", lines);
    }

    private void typePreviousRawListDeclaration(String[] lines, int currentIndex, String listName, String elementType) {
        Pattern declaration = Pattern.compile("\\bList\\s+" + Pattern.quote(listName) + "\\s*([;=])");
        for (int i = currentIndex; i >= 0; i--) {
            if (i != currentIndex && looksLikeMethodDeclaration(lines[i])) {
                return;
            }
            Matcher matcher = declaration.matcher(lines[i]);
            if (matcher.find()) {
                String suffix = matcher.group(1).equals(";") ? ";" : " =";
                lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                        "List<" + elementType + "> " + listName + suffix));
                return;
            }
        }
    }

    private String restoreStringObjectMapListTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Pattern rawMapList = Pattern.compile("\\bList\\s*<\\s*Map\\s*>\\s+([A-Za-z_$][\\w$]*)\\s*([;=])");
        Pattern rawMapFor = Pattern.compile("\\bfor\\s*\\(\\s*Map\\s+([A-Za-z_$][\\w$]*)\\s*:\\s*"
                + "([A-Za-z_$][\\w$]*)\\s*\\)");
        for (int i = 0; i < lines.length; i++) {
            Matcher listMatcher = rawMapList.matcher(lines[i]);
            while (listMatcher.find()) {
                String listName = listMatcher.group(1);
                if (!isStringKeyMapElementUsed(lines, i + 1, listName)) {
                    continue;
                }
                String suffix = listMatcher.group(2).equals(";") ? ";" : " =";
                lines[i] = listMatcher.replaceFirst(Matcher.quoteReplacement(
                        "List<Map<String, Object>> " + listName + suffix));
            }
        }
        for (int i = 0; i < lines.length; i++) {
            Matcher forMatcher = rawMapFor.matcher(lines[i]);
            while (forMatcher.find()) {
                String mapName = forMatcher.group(1);
                String listName = forMatcher.group(2);
                if (isTypedStringObjectMapList(lines, i, listName)
                        || isStringKeyMapLocalUsed(lines, i + 1, mapName)) {
                    lines[i] = forMatcher.replaceFirst(Matcher.quoteReplacement(
                            "for (Map<String, Object> " + mapName + " : " + listName + ")"));
                }
            }
        }
        return String.join("\n", lines);
    }

    private boolean isStringKeyMapElementUsed(String[] lines, int start, String listName) {
        Pattern rawMapFor = Pattern.compile("\\bfor\\s*\\(\\s*Map(?:\\s*<\\s*String\\s*,\\s*Object\\s*>)?\\s+"
                + "([A-Za-z_$][\\w$]*)\\s*:\\s*" + Pattern.quote(listName) + "\\s*\\)");
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return false;
            }
            Matcher matcher = rawMapFor.matcher(lines[i]);
            if (matcher.find() && isStringKeyMapLocalUsed(lines, i + 1, matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private boolean isStringKeyMapLocalUsed(String[] lines, int start, String mapName) {
        Pattern stringKeyGet = Pattern.compile("\\b" + Pattern.quote(mapName) + "\\.get\\s*\\(\\s*\"");
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return false;
            }
            if (stringKeyGet.matcher(lines[i]).find()) {
                return true;
            }
            if (lines[i].matches("\\s*}\\s*")) {
                return false;
            }
        }
        return false;
    }

    private boolean isTypedStringObjectMapList(String[] lines, int currentIndex, String listName) {
        Pattern typedList = Pattern.compile("\\bList\\s*<\\s*Map\\s*<\\s*String\\s*,\\s*Object\\s*>\\s*>\\s+"
                + Pattern.quote(listName) + "\\s*([;=])");
        for (int i = currentIndex; i >= 0; i--) {
            if (i != currentIndex && looksLikeMethodDeclaration(lines[i])) {
                return false;
            }
            if (typedList.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }

    private String typeNestedListEnhancedFor(String line, String partitionName, String elementType) {
        Pattern enhancedFor = Pattern.compile("\\bfor\\s*\\(\\s*List\\s+([A-Za-z_$][\\w$]*)\\s*:\\s*"
                + Pattern.quote(partitionName) + "\\s*\\)");
        Matcher matcher = enhancedFor.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    "for (List<" + elementType + "> " + matcher.group(1) + " : " + partitionName + ")"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restoreRawMapTypesFromCollectorsToMap(String source) {
        Matcher matcher = Pattern.compile("(?m)^(\\s*)Map\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*Collectors\\.toMap\\(\\s*"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::([A-Za-z_$][\\w$]*)\\s*,\\s*"
                + "(?:Function\\.identity\\(\\)|"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::([A-Za-z_$][\\w$]*))"
                + "[^;\\n]*;)").matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String ownerType = matcher.group(4);
            String keyType = inferGetterReturnType(matcher.group(5));
            String valueType = matcher.group(7) == null ? ownerType : inferGetterReturnType(matcher.group(7));
            if (keyType == null || valueType == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "Map<" + keyType + ", " + valueType + "> "
                            + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restoreGetBeansOfTypeMapTypes(String source) {
        Matcher matcher = Pattern.compile("(?m)^(\\s*)Map\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*getBeansOfType\\(\\s*([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)"
                + "\\.class\\s*\\);)").matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "Map<String, " + matcher.group(4) + "> "
                            + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restoreRawMapTypesFromGetOrDefaultDefaults(String source) {
        String[] lines = source.split("\\n", -1);
        Pattern rawMap = Pattern.compile("\\bMap\\s+([A-Za-z_$][\\w$]*)\\s*([;=])");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = rawMap.matcher(lines[i]);
            while (matcher.find()) {
                String mapName = matcher.group(1);
                String[] keyAndValueTypes = findGetOrDefaultTypes(lines, i + 1, mapName);
                if (keyAndValueTypes == null) {
                    continue;
                }
                String suffix = matcher.group(2).equals(";") ? ";" : " =";
                lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                        "Map<" + keyAndValueTypes[0] + ", " + keyAndValueTypes[1] + "> "
                                + mapName + suffix));
            }
        }
        return String.join("\n", lines);
    }

    private String[] findGetOrDefaultTypes(String[] lines, int start, String mapName) {
        Pattern newDefault = Pattern.compile("\\b" + Pattern.quote(mapName)
                + "\\.getOrDefault\\s*\\(\\s*([^,]+?)\\s*,\\s*new\\s+"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*\\(");
        Pattern literalDefault = Pattern.compile("\\b" + Pattern.quote(mapName)
                + "\\.getOrDefault\\s*\\(\\s*([^,]+?)\\s*,\\s*"
                + "(Boolean\\.(?:TRUE|FALSE)|\"[^\"]*\"|\\d+L?|\\d+\\.\\d+)\\s*\\)");
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return null;
            }
            Matcher newMatcher = newDefault.matcher(lines[i]);
            if (newMatcher.find()) {
                String keyType = inferExpressionType(newMatcher.group(1));
                if (keyType != null) {
                    return new String[]{keyType, newMatcher.group(2)};
                }
            }
            Matcher literalMatcher = literalDefault.matcher(lines[i]);
            if (literalMatcher.find()) {
                String keyType = inferExpressionType(literalMatcher.group(1));
                String valueType = inferLiteralType(literalMatcher.group(2));
                if (keyType != null && valueType != null) {
                    return new String[]{keyType, valueType};
                }
            }
        }
        return null;
    }

    private String inferExpressionType(String expression) {
        String trimmed = expression.trim();
        if (trimmed.startsWith("\"")) {
            return "String";
        }
        Matcher getter = Pattern.compile("\\.([A-Za-z_$][\\w$]*)\\s*\\(\\s*\\)\\s*$").matcher(trimmed);
        if (getter.find()) {
            return inferGetterReturnType(getter.group(1));
        }
        return null;
    }

    private String inferLiteralType(String literal) {
        if (literal.startsWith("Boolean.")) {
            return "Boolean";
        }
        if (literal.startsWith("\"")) {
            return "String";
        }
        if (literal.endsWith("L")) {
            return "Long";
        }
        if (literal.contains(".")) {
            return "Double";
        }
        return "Integer";
    }

    private String inferGetterReturnType(String methodName) {
        if (methodName.startsWith("is") || methodName.equals("getCheckRes")
                || methodName.startsWith("getIs")) {
            return "Boolean";
        }
        if (!methodName.startsWith("get") || methodName.length() <= 3) {
            return null;
        }
        String property = methodName.substring(3);
        if (property.equals("Uid") || property.endsWith("Uid")
                || property.equals("Id") || property.endsWith("Id")) {
            return "Long";
        }
        if (property.equals("Identify") || property.endsWith("Name") || property.endsWith("Account")
                || property.endsWith("Title") || property.endsWith("Content") || property.endsWith("Addr")
                || property.endsWith("Code")) {
            return "String";
        }
        if (property.endsWith("Status") || property.endsWith("Type") || property.endsWith("Count")
                || property.endsWith("Num") || property.endsWith("Size") || property.endsWith("Level")
                || property.endsWith("Channel")) {
            return "Integer";
        }
        if (property.endsWith("Amount") || property.endsWith("Balance")) {
            return "BigDecimal";
        }
        return null;
    }

    private String restoreIdentifyMapTypes(String source) {
        Matcher matcher = Pattern.compile("(?m)^(\\s*)Map\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*\\.getIdentifyMap\\([^;\\n]*;)").matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "Map<Long, String> " + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restoreMapValueTypesFromInitializers(String source) {
        Matcher matcher = Pattern.compile("(?m)^(\\s*)Map\\s*<\\s*([^,>]+)\\s*,\\s*Object\\s*>\\s+"
                + "([A-Za-z_$][\\w$]*)\\s*=\\s*new\\s+([A-Za-z_$][\\w$.]*)\\s*<\\s*\\2\\s*,\\s*"
                + "([^>]+?)\\s*>\\s*\\(([^;\\n]*)\\);").matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String keyType = matcher.group(2).trim();
            String valueType = matcher.group(5).trim();
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "Map<" + keyType + ", " + valueType + "> " + matcher.group(3)
                            + " = new " + matcher.group(4) + "<" + keyType + ", " + valueType + ">("
                            + matcher.group(6) + ");"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restoreMapEntryEnhancedForTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Pattern rawEntryFor = Pattern.compile("\\bfor\\s*\\(\\s*Map\\.Entry\\s+([A-Za-z_$][\\w$]*)\\s*:\\s*"
                + "[^\\n;]+\\.entrySet\\(\\)\\s*\\)");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = rawEntryFor.matcher(lines[i]);
            while (matcher.find()) {
                String entryName = matcher.group(1);
                String keyType = findEntryKeyType(lines, i + 1, entryName);
                String valueType = findEntryValueType(lines, i + 1, entryName);
                if (keyType == null || valueType == null) {
                    continue;
                }
                lines[i] = matcher.replaceFirst(Matcher.quoteReplacement(
                        "for (Map.Entry<" + keyType + ", " + valueType + "> " + entryName + " : "
                                + rawEntryForIterable(lines[i]) + ")"));
            }
        }
        return String.join("\n", lines);
    }

    private String rawEntryForIterable(String line) {
        Matcher matcher = Pattern.compile("\\bfor\\s*\\(\\s*Map\\.Entry\\s+[A-Za-z_$][\\w$]*\\s*:\\s*"
                + "([^\\n;]+\\.entrySet\\(\\))\\s*\\)").matcher(line);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String findEntryKeyType(String[] lines, int start, String entryName) {
        Pattern keyAssignment = Pattern.compile("\\b([A-Za-z_$][\\w$.<>]*)\\s+[A-Za-z_$][\\w$]*\\s*=\\s*"
                + Pattern.quote(entryName) + "\\.getKey\\(\\)");
        return findEntryMemberType(lines, start, keyAssignment);
    }

    private String findEntryValueType(String[] lines, int start, String entryName) {
        Pattern valueAssignment = Pattern.compile("\\b([A-Za-z_$][\\w$.<>]*)\\s+[A-Za-z_$][\\w$]*\\s*=\\s*"
                + "(?:\\(([^)]+)\\))?" + Pattern.quote(entryName) + "\\.getValue\\(\\)");
        return findEntryMemberType(lines, start, valueAssignment);
    }

    private String findEntryMemberType(String[] lines, int start, Pattern assignment) {
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return null;
            }
            Matcher matcher = assignment.matcher(lines[i]);
            if (matcher.find()) {
                String castType = matcher.groupCount() >= 2 ? matcher.group(2) : null;
                return castType == null ? matcher.group(1) : castType.trim();
            }
            if (lines[i].matches("\\s*}\\s*")) {
                return null;
            }
        }
        return null;
    }

    private String widenShiroFilterMapTypes(String source) {
        Matcher setFiltersMatcher = Pattern.compile("\\.setFilters\\(\\s*([A-Za-z_$][\\w$]*)\\s*\\)").matcher(source);
        String processed = source;
        while (setFiltersMatcher.find()) {
            String filtersName = setFiltersMatcher.group(1);
            Pattern declaration = Pattern.compile("\\bLinkedHashMap\\s*<\\s*String\\s*,\\s*"
                    + "[A-Za-z_$][\\w$.]*\\s*>\\s+" + Pattern.quote(filtersName)
                    + "\\s*=\\s*new\\s+LinkedHashMap\\s*<\\s*String\\s*,\\s*[A-Za-z_$][\\w$.]*\\s*>\\s*\\(");
            Matcher declarationMatcher = declaration.matcher(processed);
            StringBuffer buffer = new StringBuffer();
            while (declarationMatcher.find()) {
                declarationMatcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        "LinkedHashMap<String, jakarta.servlet.Filter> " + filtersName
                                + " = new LinkedHashMap<String, jakarta.servlet.Filter>("));
            }
            declarationMatcher.appendTail(buffer);
            processed = buffer.toString();
        }
        return processed;
    }

    private String restoreGenericListMethodTypeVariableLocals(String source) {
        String[] lines = source.split("\\n", -1);
        String currentTypeVariable = null;
        Map<String, String> typedCollections = new LinkedHashMap<>();
        Pattern genericListMethod = Pattern.compile(
                "<[^>]*\\b([A-Z][A-Za-z0-9_$]*)\\b[^>]*>\\s+List\\s*<\\s*\\1\\s*>\\s+[A-Za-z_$][\\w$]*\\s*\\(");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher methodMatcher = genericListMethod.matcher(line);
            if (methodMatcher.find()) {
                currentTypeVariable = methodMatcher.group(1);
                typedCollections.clear();
            } else if (looksLikeMethodDeclaration(line)) {
                currentTypeVariable = null;
                typedCollections.clear();
            }
            if (currentTypeVariable == null) {
                continue;
            }

            Matcher collectionMatcher = Pattern.compile("\\bArrayList<Object>\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                    + "new\\s+ArrayList<Object>\\(").matcher(line);
            StringBuffer collectionBuffer = new StringBuffer();
            while (collectionMatcher.find()) {
                String collectionName = collectionMatcher.group(1);
                typedCollections.put(collectionName, currentTypeVariable);
                collectionMatcher.appendReplacement(collectionBuffer, Matcher.quoteReplacement(
                        "ArrayList<" + currentTypeVariable + "> " + collectionName
                                + " = new ArrayList<" + currentTypeVariable + ">("));
            }
            collectionMatcher.appendTail(collectionBuffer);
            line = collectionBuffer.toString();

            for (Map.Entry<String, String> entry : typedCollections.entrySet()) {
                Matcher objectLocal = Pattern.compile("\\bObject\\s+([A-Za-z_$][\\w$]*)\\s*=").matcher(line);
                if (objectLocal.find() && isAddedBeforeNextMethod(lines, i + 1, entry.getKey(), objectLocal.group(1))) {
                    line = objectLocal.replaceFirst(Matcher.quoteReplacement(entry.getValue() + " "
                            + objectLocal.group(1) + " ="));
                    break;
                }
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private boolean isAddedBeforeNextMethod(String[] lines, int start, String collectionName, String localName) {
        Pattern addCall = Pattern.compile("\\b" + Pattern.quote(collectionName)
                + "\\.add\\s*\\(\\s*" + Pattern.quote(localName) + "\\s*\\)");
        for (int i = start; i < lines.length; i++) {
            if (looksLikeMethodDeclaration(lines[i])) {
                return false;
            }
            if (addCall.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }

    private String restoreLocalTypesFromGenericMethodReturns(String source) {
        Matcher declarationMatcher = GENERIC_METHOD_DECLARATION.matcher(source);
        Map<String, String> returnTypes = new LinkedHashMap<>();
        while (declarationMatcher.find()) {
            returnTypes.putIfAbsent(declarationMatcher.group(2), normalizeGenericType(declarationMatcher.group(1)));
        }

        String processed = source;
        for (Map.Entry<String, String> entry : returnTypes.entrySet()) {
            String genericType = entry.getValue();
            String rawSimpleName = rawSimpleTypeName(genericType);
            Pattern assignment = Pattern.compile("(?m)^(\\s*)"
                    + Pattern.quote(rawSimpleName)
                    + "\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*((?:this\\.|[A-Za-z_$][\\w$]*\\.)?"
                    + Pattern.quote(entry.getKey())
                    + "\\s*\\([^;\\n]*;)");
            Matcher matcher = assignment.matcher(processed);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        matcher.group(1) + genericType + " " + matcher.group(2) + " = " + matcher.group(3)));
            }
            matcher.appendTail(buffer);
            processed = buffer.toString();
        }
        return processed;
    }

    private String restoreRawListElementTypesFromStreamMethodReferences(String source) {
        Matcher matcher = RAW_LIST_DECLARATION_WITH_INITIALIZER.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String listName = matcher.group(2);
            String initializer = matcher.group(3);
            if (initializer.contains(".stream().map(")) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String elementType = findStreamMethodReferenceOwner(source, matcher.end(), listName);
            if (elementType == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "List<" + elementType + "> " + listName + " = " + initializer));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String findStreamMethodReferenceOwner(String source, int start, String listName) {
        Pattern streamMethodReference = Pattern.compile("\\b" + Pattern.quote(listName)
                + "\\.stream\\(\\)\\.map\\(\\s*([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::"
                + "([A-Za-z_$][\\w$]*)");
        String methodRemainder = source.substring(start, nextMethodStart(source, start));
        Matcher matcher = streamMethodReference.matcher(methodRemainder);
        if (matcher.find() && isBeanAccessorMethodReference(matcher.group(2))) {
            return matcher.group(1);
        }

        Pattern collectorsToMapMethodReference = Pattern.compile("\\b" + Pattern.quote(listName)
                + "\\.stream\\(\\)\\.collect\\(\\s*Collectors\\.toMap\\(\\s*"
                + "([A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][\\w$]*)*)::([A-Za-z_$][\\w$]*)");
        Matcher toMapMatcher = collectorsToMapMethodReference.matcher(methodRemainder);
        if (toMapMatcher.find() && isBeanAccessorMethodReference(toMapMatcher.group(2))) {
            return toMapMatcher.group(1);
        }
        return null;
    }

    private int nextMethodStart(String source, int start) {
        Matcher matcher = Pattern.compile("(?m)^\\s*(?:public|protected|private)\\s+[^=;{}]+\\([^;{}]*\\)\\s*\\{?")
                .matcher(source);
        if (matcher.find(start)) {
            return matcher.start();
        }
        return source.length();
    }

    private String alignStreamMethodReferenceOwnersWithListElementTypes(String source) {
        String[] lines = source.split("\\n", -1);
        Map<String, String> listElementTypes = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (looksLikeMethodDeclaration(line)) {
                listElementTypes.clear();
            }
            Matcher declarationMatcher = TYPED_LIST_DECLARATION.matcher(line);
            while (declarationMatcher.find()) {
                listElementTypes.put(declarationMatcher.group(2), declarationMatcher.group(1));
            }
            for (Map.Entry<String, String> entry : listElementTypes.entrySet()) {
                line = alignStreamMethodReferenceOwnerInLine(line, entry.getKey(), entry.getValue());
            }
            lines[i] = line;
        }
        return String.join("\n", lines);
    }

    private String alignStreamMethodReferenceOwnerInLine(String line, String listName, String elementType) {
            Pattern streamMap = Pattern.compile("\\b"
                    + Pattern.quote(listName)
                    + "\\.stream\\(\\)\\.map\\(\\s*([A-Za-z_$][\\w$.]*)::([A-Za-z_$][\\w$]*)");
            Matcher matcher = streamMap.matcher(line);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                if (!isBeanAccessorMethodReference(matcher.group(2))) {
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                    continue;
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        listName + ".stream().map(" + elementType + "::" + matcher.group(2)));
            }
            matcher.appendTail(buffer);
            return buffer.toString();
    }

    private boolean isBeanAccessorMethodReference(String methodName) {
        return methodName.matches("(?:get|is)[A-Z].*");
    }

    private String restoreStringSplitArrayTypes(String source) {
        Matcher matcher = CHAR_SEQUENCE_SPLIT_ARRAY.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "String[] " + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String addImmutableMapBuilderTypeArguments(String source) {
        String[] lines = source.split("\\n", -1);
        String currentMapArgs = null;
        int braceDepth = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher methodMatcher = MAP_METHOD_DECLARATION.matcher(line);
            if (methodMatcher.find()) {
                currentMapArgs = methodMatcher.group(1).trim() + ", " + methodMatcher.group(2).trim();
                braceDepth = 0;
            }
            if (currentMapArgs != null && line.contains("ImmutableMap.builder()")) {
                line = line.replace("ImmutableMap.builder()", "ImmutableMap.<" + currentMapArgs + ">builder()");
            }
            if (currentMapArgs != null && line.contains("ImmutableMap.<" + currentMapArgs + ">builder()")) {
                line = castImmutableMapLambdaToCurrentMapType(line, currentMapArgs);
            }
            lines[i] = line;
            if (currentMapArgs != null) {
                braceDepth += countChar(line, '{') - countChar(line, '}');
                if (braceDepth <= 0) {
                    currentMapArgs = null;
                }
            }
        }
        return String.join("\n", lines);
    }

    private String castImmutableMapLambdaToCurrentMapType(String line, String mapArgs) {
        Pattern lambdaBuilder = Pattern.compile("\\.map\\(\\s*([A-Za-z_$][\\w$]*)\\s*->\\s*"
                + "ImmutableMap\\.<" + Pattern.quote(mapArgs) + ">builder\\(\\)");
        Matcher matcher = lambdaBuilder.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    ".map(" + matcher.group(1) + " -> (Map<" + mapArgs + ">)ImmutableMap.<"
                            + mapArgs + ">builder()"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String castOptionalOrElseGetMapSuppliers(String source) {
        String[] lines = source.split("\\n", -1);
        String currentMapArgs = null;
        for (int i = 0; i < lines.length; i++) {
            Matcher methodMatcher = MAP_METHOD_DECLARATION.matcher(lines[i]);
            if (methodMatcher.find()) {
                currentMapArgs = methodMatcher.group(1).trim() + ", " + methodMatcher.group(2).trim();
            } else if (looksLikeMethodDeclaration(lines[i])) {
                currentMapArgs = null;
            }
            if (currentMapArgs != null && lines[i].contains(".orElseGet(() -> ")
                    && !lines[i].contains(".orElseGet(() -> (Map<")) {
                lines[i] = lines[i].replace(".orElseGet(() -> ",
                        ".orElseGet(() -> (Map<" + currentMapArgs + ">)");
            }
        }
        return String.join("\n", lines);
    }

    private int countChar(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private String removeGuavaListFactoryCasts(String source) {
        String processed = source.replaceAll("Lists\\.newArrayList\\(\\s*\\(Object\\[\\]\\)\\s*",
                "Lists.newArrayList(");
        return processed.replaceAll("Lists\\.newArrayList\\(\\s*\\(Iterable\\)\\s*", "Lists.newArrayList(");
    }

    private String removeGuavaPartitionListCasts(String source) {
        return source.replaceAll("Lists\\.partition\\(\\s*\\(List\\)\\s*", "Lists.partition(");
    }

    private String removeRedundantLiteralCasts(String source) {
        String processed = source.replaceAll("\\(String\\)\\s*(\"(?:\\\\.|[^\"\\\\])*\")", "$1");
        processed = processed.replaceAll("\\(int\\)\\s*(-?\\d+)(?![lL])", "$1");
        return processed.replaceAll("\\(long\\)\\s*(-?\\d+[lL])", "$1");
    }

    private String removeCollectionUtilityCasts(String source) {
        return source.replaceAll("((?:CollectionUtils|CollUtil)\\.is(?:Empty|NotEmpty)\\(\\s*)"
                + "\\((?:java\\.util\\.)?Collection(?:\\s*<\\s*\\?\\s*>)?\\)\\s*", "$1");
    }

    private String restoreLettuceTimeoutAutounboxing(String source) {
        return source.replaceAll("(lettuceConnectionFactory\\([^;\\n]*?,\\s*)([A-Za-z_$][\\w$]*)\\.longValue\\(\\)",
                "$1$2");
    }

    private String restoreSameClassStringConstantReferences(String source) {
        Matcher matcher = SAME_CLASS_STRING_CONSTANT_DECLARATION.matcher(source);
        Map<String, String> constantsByLiteral = new LinkedHashMap<>();
        Set<String> duplicateLiterals = new LinkedHashSet<>();
        while (matcher.find()) {
            String literal = matcher.group(2);
            String existing = constantsByLiteral.putIfAbsent(literal, matcher.group(1));
            if (existing != null) {
                duplicateLiterals.add(literal);
            }
        }
        duplicateLiterals.forEach(constantsByLiteral::remove);
        if (constantsByLiteral.isEmpty()) {
            return source;
        }

        String[] lines = source.split("\n", -1);
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("@")
                    || trimmed.startsWith("package ")
                    || trimmed.startsWith("import ")
                    || SAME_CLASS_STRING_CONSTANT_DECLARATION.matcher(line).find()) {
                continue;
            }
            String restored = line;
            for (Map.Entry<String, String> entry : constantsByLiteral.entrySet()) {
                restored = restored.replace(entry.getKey(), entry.getValue());
            }
            if (!restored.equals(line)) {
                lines[i] = restored;
                changed = true;
            }
        }
        return changed ? String.join("\n", lines) : source;
    }

    private String unwrapSFunctionArraySelects(String source) {
        Matcher matcher = Pattern.compile("\\.select\\(\\s*new\\s+SFunction\\s*\\[\\]\\s*\\{\\s*([^}]*)\\s*}\\s*\\)")
                .matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(".select(" + matcher.group(1).trim() + ")"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String removeMyBatisWrapperCasts(String source) {
        return source.replaceAll("\\(Wrapper\\)\\s*(new\\s+Lambda(?:Query|Update)Wrapper\\s*<)", "$1");
    }

    private String restoreStringListLocalsFromFactoryAssignments(String source) {
        Matcher matcher = RAW_ARRAY_LIST_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String localName = matcher.group(2);
            if (isAssignedFromStringListFactory(source, localName)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        matcher.group(1) + "java.util.List<String> " + localName + ";"));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isAssignedFromStringListFactory(String source, String localName) {
        Pattern arraysAsList = Pattern.compile("\\b" + Pattern.quote(localName)
                + "\\s*=\\s*Arrays\\.asList\\s*\\(");
        Pattern annotationValues = Pattern.compile("\\b" + Pattern.quote(localName)
                + "\\s*=\\s*Lists\\.newArrayList\\s*\\(\\s*[^;\\n]*\\.value\\(\\)\\s*\\)");
        return arraysAsList.matcher(source).find() && annotationValues.matcher(source).find();
    }

    private String normalizeGenericType(String type) {
        return type.replaceAll("\\s+", " ").trim();
    }

    private String rawSimpleTypeName(String genericType) {
        int genericStart = genericType.indexOf('<');
        String rawType = genericStart >= 0 ? genericType.substring(0, genericStart).trim() : genericType.trim();
        int lastDot = rawType.lastIndexOf('.');
        return lastDot >= 0 ? rawType.substring(lastDot + 1) : rawType;
    }

    private String removeRedundantImports(String source, String className) {
        String packageName = findPackageName(source, className);
        Matcher matcher = IMPORT_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String imported = matcher.group(1);
            if (isImplicitImport(imported, packageName)) {
                matcher.appendReplacement(buffer, "");
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String findPackageName(String source, String className) {
        Matcher matcher = PACKAGE_DECLARATION.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (className == null) {
            return "";
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    private boolean isImplicitImport(String imported, String packageName) {
        if (imported == null || imported.isEmpty()) {
            return false;
        }
        if (imported.startsWith("java.lang.") && imported.indexOf('.', "java.lang.".length()) < 0) {
            return true;
        }
        if (packageName == null || packageName.isEmpty() || !imported.startsWith(packageName + ".")) {
            return false;
        }
        String suffix = imported.substring(packageName.length() + 1);
        return !suffix.contains(".");
    }

    private String removeParameterArrayCasts(String source) {
        Matcher matcher = Pattern.compile("\\((String\\[\\])\\)(\\w+)").matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(2);
            if (Pattern.compile("String\\[\\]\\s+" + Pattern.quote(variableName) + "\\b").matcher(source).find()) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(variableName));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String restoreStringLocalsFromToStringAssignments(String source) {
        Matcher matcher = Pattern.compile("(?m)^(\\s*)Object\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*"
                + "([^;\\n]*\\.toString\\(\\)\\s*;)").matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "String " + matcher.group(2) + " = " + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String unwrapSingleElementRedisSetOperationArrays(String source) {
        Pattern singleElementSetOperationArray = Pattern.compile("(opsForSet\\(\\)\\.(?:add|remove)\\s*"
                + "\\([^\\n;]*?,\\s*)(?:\\(Object\\[\\]\\)\\s*)?new\\s+(?:String|Object)\\[\\]\\s*"
                + "\\{\\s*([^{};]+?)\\s*}\\s*\\)");
        Matcher matcher = singleElementSetOperationArray.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2).trim() + ")"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String addListElementTypes(String source) {
        Matcher matcher = RAW_COLLECTION_ENHANCED_FOR.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String collectionType = matcher.group(1);
            String collectionName = matcher.group(2);
            String initializer = addConstructorElementType(matcher.group(3), collectionType, matcher.group(5).trim());
            String indent = matcher.group(4);
            String elementType = matcher.group(5).trim();
            String elementName = matcher.group(6);
            String replacement = collectionType + "<" + elementType + "> " + collectionName + " = " + initializer + ";\n"
                    + indent + "for (" + elementType + " " + elementName + " : " + collectionName + ")";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String addConstructorElementType(String initializer, String collectionType, String elementType) {
        return initializer.replaceFirst("\\bnew\\s+" + Pattern.quote(collectionType) + "\\s*\\(",
                "new " + collectionType + "<" + elementType + ">(");
    }

    private String addOptionalElementTypes(String source) {
        Matcher matcher = RAW_OPTIONAL_OR_ELSE_THROW.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String optionalName = matcher.group(1);
            String initializer = matcher.group(2);
            String indent = matcher.group(3);
            String variableType = matcher.group(4);
            String variableName = matcher.group(5);
            String castType = matcher.group(6);
            String optionalType = castType.trim();
            String replacement = "Optional<" + optionalType + "> " + optionalName + " = " + initializer + ";\n"
                    + indent + variableType + " " + variableName + " = " + optionalName + ".orElseThrow";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String addNumericGenericElementTypes(String source) {
        Matcher matcher = NUMERIC_GENERIC_COLLECTION_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(3);
            String elementType = findEnhancedForElementType(source, matcher.end(), variableName);
            if (elementType == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String replacement = matcher.group(1) + matcher.group(2) + "<" + elementType + "> "
                    + variableName + " = new " + matcher.group(4) + "<" + elementType + ">("
                    + matcher.group(5) + ");";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String addObjectElementTypesToRawListCasts(String source) {
        Matcher matcher = RAW_LIST_CAST_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            if (isConsumedByFirstElement(source, matcher.end(), matcher.group(2))) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        matcher.group(1) + "List<Object> " + matcher.group(2) + " = (List<Object>)"));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String widenExecutionExceptionCauseLocals(String source) {
        Matcher matcher = EXECUTION_EXCEPTION_CAUSE_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "Exception " + matcher.group(2) + " = " + matcher.group(3) + ";"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceAnsiTextSyntheticOuterReferences(String source) {
        return source.replace("Help.defaultColorScheme(this$0)", "Help.defaultColorScheme(Ansi.this)");
    }

    private String removeUndefinedGenericToArrayCasts(String source) {
        return UNDEFINED_GENERIC_TO_ARRAY_CAST.matcher(source).replaceAll(".toArray(new ");
    }

    private String castClassWildcardsForEnumFactories(String source) {
        String processed = ENUM_SET_NONE_OF_WILDCARD_CLASS.matcher(source)
                .replaceAll("EnumSet.noneOf((Class) $1)");
        processed = ENUM_VALUE_OF_WILDCARD_CLASS.matcher(processed)
                .replaceAll("Enum.valueOf((Class) $1,");
        if (processed.contains("EnumSet<?> enumSet")) {
            processed = processed.replace("return enumSet;", "return (Collection<Object>) enumSet;");
        }
        return processed;
    }

    private String restoreGenericDefaultExceptionHandlerConstruction(String source) {
        return source.replace("parseWithHandlers(handler, new DefaultExceptionHandler(), args)",
                "parseWithHandlers(handler, new DefaultExceptionHandler<R>(), args)");
    }

    private String restorePositionalParamSpecListLocals(String source) {
        if (!source.contains("positionals.subList")) {
            return source;
        }
        return source.replace("List<Object> missingList = Collections.emptyList();",
                "List<Model.PositionalParamSpec> missingList = Collections.emptyList();");
    }

    private String restoreWildcardClassReflectionLocals(String source) {
        if (!source.contains("getSuperclass()") && !source.contains("getAnnotation(Command.class)")) {
            return source;
        }
        return source
                .replace("Class<Object>", "Class<?>")
                .replace("Class cls;", "Class<?> cls;")
                .replace("Stack<Class> hierarchy = new Stack<Class>();",
                        "Stack<Class<?>> hierarchy = new Stack<Class<?>>();")
                .replace("cls = (Class)hierarchy.pop();", "cls = hierarchy.pop();")
                .replace("cls = (Class<Object>)hierarchy.pop();", "cls = hierarchy.pop();");
    }

    private String castWildcardClassArrayElements(String source) {
        if (!source.contains("Class<?>[] aux")) {
            return source;
        }
        return source.replace("result.add(aux[0]);", "result.add((Class) aux[0]);");
    }

    private String restoreGenericReturnLocalTypes(String source) {
        if (!source.contains("public V put(") || !source.contains("return removedValue;")) {
            return source;
        }
        return source.replace("Object removedValue =", "V removedValue =");
    }

    private String restoreDeferredAssignments(String source) {
        String[] lines = source.split("\\n", -1);
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < lines.length; i++) {
            String replacement = null;
            if (i + 1 < lines.length && "String result;".equals(lines[i].trim())) {
                replacement = restoreNestedStringAssignment(lines[i + 1]);
                if (replacement != null) {
                    i++;
                }
            } else if (i + 1 < lines.length && "int result;".equals(lines[i].trim())) {
                replacement = restoreNestedIntAssignment(lines[i + 1], "n", true);
                if (replacement != null) {
                    i++;
                }
            } else if (lines[i].contains("int n2 =") && lines[i].contains("(result = ")) {
                replacement = restoreNestedIntAssignment(lines[i], "n2", false);
            } else if (lines[i].contains("int n =") && lines[i].contains("(result = ")) {
                replacement = restoreNestedIntAssignment(lines[i], "n", false);
            } else if (i + 1 < lines.length && "String gram;".equals(lines[i].trim())) {
                replacement = splitMapPutAssignedKey(lines[i], lines[i + 1]);
                if (replacement != null) {
                    i++;
                }
            }
            appendLine(builder, replacement == null ? lines[i] : replacement);
        }
        return builder.toString();
    }

    private String restoreNestedStringAssignment(String line) {
        if (!line.contains("String string =") || !line.contains("(result = ")) {
            return null;
        }
        return line.replaceFirst("String\\s+string\\s*=", "String result =")
                .replace("(result = ", "(");
    }

    private String restoreNestedIntAssignment(String line, String temporaryName, boolean declareResult) {
        String marker = "int " + temporaryName + " =";
        int markerIndex = line.indexOf(marker);
        if (markerIndex < 0 || !line.contains("(result = ")) {
            return null;
        }
        String prefix = line.substring(0, markerIndex);
        String expression = line.substring(markerIndex + marker.length()).trim()
                .replace("(result = ", "(");
        return prefix + (declareResult ? "int result = " : "result = ") + expression;
    }

    private String splitMapPutAssignedKey(String declarationLine, String putLine) {
        String marker = "m.put(gram, 1 + (m.containsKey(gram = ";
        int markerIndex = putLine.indexOf(marker);
        if (markerIndex < 0 || !putLine.contains(") ? (Integer)m.get(gram) : 0));")) {
            return null;
        }
        String indent = declarationLine.substring(0, declarationLine.indexOf("String gram;"));
        String expression = putLine.substring(markerIndex + marker.length(),
                putLine.indexOf(") ? (Integer)m.get(gram) : 0));", markerIndex));
        String putReplacement = putLine.substring(0, markerIndex)
                + "m.put(gram, 1 + (m.containsKey(gram) ? (Integer)m.get(gram) : 0));";
        return indent + "String gram = " + expression + ";\n" + putReplacement;
    }

    private void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private String widenRegexTransformerConditionals(String source) {
        Matcher matcher = REGEX_TRANSFORMER_CONDITIONAL_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "INegatableOptionTransformer " + matcher.group(2) + " = " + matcher.group(3) + ";"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String hoistForLoopCountersReferencedByLaterLoops(String source) {
        Matcher matcher = FOR_LOOP_COUNTER_DECLARATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(2);
            if (isForLoopCounterReferencedByLaterLoop(source, matcher.end(), variableName)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        matcher.group(1) + "int " + variableName + ";\n"
                                + matcher.group(1) + "for (" + variableName + " = " + matcher.group(3) + ";"));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isForLoopCounterReferencedByLaterLoop(String source, int startOffset, String variableName) {
        Pattern pattern = Pattern.compile("\\bfor\\s*\\([^;\\n]*=\\s*" + Pattern.quote(variableName) + "\\s*;");
        return pattern.matcher(source).find(startOffset);
    }

    private boolean isConsumedByFirstElement(String source, int startOffset, String variableName) {
        Pattern pattern = Pattern.compile("\\bfirstElement\\s*\\(\\s*" + Pattern.quote(variableName) + "\\s*\\)");
        return pattern.matcher(source).find(startOffset);
    }

    private String findEnhancedForElementType(String source, int startOffset, String iterableName) {
        Pattern pattern = Pattern.compile(
                "for\\s*\\(\\s*([^:\\n]+?)\\s+[A-Za-z_$][\\w$]*\\s*:\\s*"
                        + Pattern.quote(iterableName) + "\\s*\\)");
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find(startOffset)) {
            return null;
        }
        String elementType = matcher.group(1).trim();
        return elementType.isEmpty() ? null : elementType;
    }

    private String replaceUnavailableAnonymousInnerClasses(String source) {
        return source
                .replace("new /* Unavailable Anonymous Inner Class!! */", "null")
                .replace("new /* invalid duplicate definition of identical inner class */", "null");
    }

    private String removeWildcardBoundsFromLambdaParameters(String source) {
        String[] lines = source.split("\\n", -1);
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(removeWildcardBoundsFromLambdaParameterLine(lines[i]));
        }
        return builder.toString();
    }

    private String removeWildcardBoundsFromLambdaParameterLine(String line) {
        int arrow = line.indexOf("->");
        if (arrow < 0 || line.indexOf('?') < 0) {
            return line;
        }
        int openParen = line.lastIndexOf('(', arrow);
        int closeParen = line.lastIndexOf(')', arrow);
        if (openParen < 0 || closeParen < openParen) {
            return line;
        }

        String[] params = line.substring(openParen + 1, closeParen).split(",");
        StringBuilder replacement = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                replacement.append(", ");
            }
            replacement.append(removeWildcardBoundFromLambdaParameter(params[i].trim()));
        }
        return line.substring(0, openParen + 1) + replacement + line.substring(closeParen);
    }

    private String removeWildcardBoundFromLambdaParameter(String parameter) {
        Matcher matcher = WILDCARD_LAMBDA_PARAMETER.matcher(parameter);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return parameter;
    }

    private String restoreSyntheticEnumSwitches(String source, String className,
                                                Map<String, Map<Integer, String>> switchCaseMaps) {
        if (!source.contains(".$SwitchMap$") || switchCaseMaps == null || switchCaseMaps.isEmpty()) {
            return source;
        }

        Matcher matcher = SYNTHETIC_ENUM_SWITCH_SELECTOR.matcher(source);
        StringBuilder builder = new StringBuilder(source.length());
        int lastAppend = 0;
        int searchFrom = 0;
        while (matcher.find(searchFrom)) {
            String fieldName = matcher.group(1);
            Map<Integer, String> caseMap = findSwitchCaseMap(switchCaseMaps, className, fieldName);
            if (caseMap == null || caseMap.isEmpty()) {
                searchFrom = matcher.end();
                continue;
            }

            int openBrace = findNextChar(source, '{', matcher.end());
            int closeBrace = findMatchingBrace(source, openBrace);
            if (openBrace < 0 || closeBrace < 0) {
                searchFrom = matcher.end();
                continue;
            }

            builder.append(source, lastAppend, matcher.start());
            builder.append("switch (").append(matcher.group(2).trim()).append(")");
            builder.append(replaceSyntheticEnumSwitchCases(source.substring(openBrace, closeBrace + 1), caseMap));
            lastAppend = closeBrace + 1;
            searchFrom = closeBrace + 1;
        }
        if (lastAppend == 0) {
            return source;
        }
        builder.append(source, lastAppend, source.length());
        return builder.toString();
    }

    private Map<Integer, String> findSwitchCaseMap(Map<String, Map<Integer, String>> switchCaseMaps,
                                                   String className,
                                                   String fieldName) {
        if (className != null && !className.isEmpty()) {
            Map<Integer, String> exact = switchCaseMaps.get(className + "#" + fieldName);
            if (exact != null) {
                return exact;
            }
            Map<Integer, String> sourceStyle = switchCaseMaps.get(className.replace('$', '.') + "#" + fieldName);
            if (sourceStyle != null) {
                return sourceStyle;
            }
        }
        return switchCaseMaps.get(fieldName);
    }

    private String replaceSyntheticEnumSwitchCases(String switchBlock, Map<Integer, String> caseMap) {
        Matcher matcher = SYNTHETIC_ENUM_SWITCH_CASE.matcher(switchBlock);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String enumConstant = caseMap.get(Integer.parseInt(matcher.group(2)));
            if (enumConstant == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                        matcher.group(1) + enumConstant + matcher.group(3)));
            }
        }
        matcher.appendTail(buffer);
        return SYNTHETIC_ENUM_SWITCH_MATCH_EXCEPTION_DEFAULT.matcher(buffer.toString()).replaceAll("");
    }

    private int findNextChar(String source, char target, int startOffset) {
        for (int i = Math.max(0, startOffset); i < source.length(); i++) {
            if (source.charAt(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private String replaceNumericAnonymousClassFragments(String source) {
        String processed = NUMERIC_ANONYMOUS_CAST.matcher(source).replaceAll("null");
        processed = NUMERIC_ANONYMOUS_CONSTRUCTOR.matcher(processed).replaceAll("null");

        Matcher matcher = NUMERIC_ANONYMOUS_TYPE_DECLARATION.matcher(processed);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "Object " + matcher.group(2) + " ="));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String shortenQualifiedInnerInstanceCreations(String source) {
        Matcher matcher = QUALIFIED_INNER_INSTANCE_CREATION.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(".new " + matcher.group(1) + "("));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceCfrVoidTemporaryLocals(String source) {
        Matcher matcher = CFR_VOID_TEMPORARY_LOCAL.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + "Object " + matcher.group(2) + " = null;"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String balanceNullArgumentStatements(String source) {
        String[] lines = source.split("\\n", -1);
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(balanceNullArgumentStatement(lines[i]));
        }
        return builder.toString();
    }

    private String balanceNullArgumentStatement(String line) {
        String trimmed = line.trim();
        if (!trimmed.endsWith("null;")) {
            return line;
        }

        int missingClosers = count(line, '(') - count(line, ')');
        if (missingClosers <= 0) {
            return line;
        }

        int semicolon = line.lastIndexOf(';');
        StringBuilder builder = new StringBuilder(line.length() + missingClosers);
        builder.append(line, 0, semicolon);
        for (int i = 0; i < missingClosers; i++) {
            builder.append(')');
        }
        builder.append(line.substring(semicolon));
        return builder.toString();
    }

    private String castDoPrivilegedMethodReferences(String source) {
        StringBuilder builder = new StringBuilder(source.length() + 32);
        int searchIndex = 0;
        while (true) {
            int callIndex = source.indexOf("AccessController.doPrivileged(", searchIndex);
            if (callIndex < 0) {
                builder.append(source, searchIndex, source.length());
                return builder.toString();
            }

            int lineStart = source.lastIndexOf('\n', callIndex) + 1;
            String prefix = source.substring(lineStart, callIndex);
            if (!prefix.trim().startsWith("return")) {
                builder.append(source, searchIndex, callIndex + 1);
                searchIndex = callIndex + 1;
                continue;
            }

            int openParen = callIndex + "AccessController.doPrivileged".length();
            int closeParen = findMatchingParen(source, openParen);
            if (closeParen < 0) {
                builder.append(source, searchIndex, callIndex + 1);
                searchIndex = callIndex + 1;
                continue;
            }

            int semicolon = closeParen + 1;
            while (semicolon < source.length() && Character.isWhitespace(source.charAt(semicolon))) {
                semicolon++;
            }
            if (semicolon >= source.length() || source.charAt(semicolon) != ';') {
                builder.append(source, searchIndex, callIndex + 1);
                searchIndex = callIndex + 1;
                continue;
            }

            String returnType = findEnclosingMethodReturnType(source, callIndex);
            if (returnType == null || "void".equals(returnType)) {
                builder.append(source, searchIndex, semicolon + 1);
            } else {
                builder.append(source, searchIndex, openParen + 1);
                builder.append("(PrivilegedAction<").append(returnType).append(">) ");
                builder.append(source, openParen + 1, semicolon + 1);
            }
            searchIndex = semicolon + 1;
        }
    }

    private String findEnclosingMethodReturnType(String source, int offset) {
        int depth = 0;
        for (int i = offset; i >= 0; i--) {
            char current = source.charAt(i);
            if (current == '}') {
                depth++;
            } else if (current == '{') {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                String returnType = extractMethodReturnType(source, i);
                if (returnType != null) {
                    return returnType;
                }
            }
        }
        return null;
    }

    private String extractMethodReturnType(String source, int openBrace) {
        int signatureStart = Math.max(0, source.lastIndexOf('\n', openBrace) + 1);
        String signature = source.substring(signatureStart, openBrace).trim();
        signature = signature.replaceAll("\\s+throws\\s+.+$", "").trim();
        signature = signature.replaceAll("^(?:public|protected|private|static|final|synchronized|native|abstract|strictfp|default|\\s)+", "").trim();
        signature = signature.replaceAll("^<[^>]+>\\s+", "").trim();
        int openParen = signature.indexOf('(');
        if (openParen < 0) {
            return null;
        }
        String beforeParen = signature.substring(0, openParen).trim();
        if (isControlBlockKeyword(beforeParen)) {
            return null;
        }
        int lastSpace = signature.lastIndexOf(' ', openParen);
        if (lastSpace < 0) {
            return null;
        }
        String returnType = signature.substring(0, lastSpace).trim();
        return returnType.isEmpty() ? null : returnType;
    }

    private boolean isControlBlockKeyword(String beforeParen) {
        return "if".equals(beforeParen)
                || "for".equals(beforeParen)
                || "while".equals(beforeParen)
                || "switch".equals(beforeParen)
                || "catch".equals(beforeParen)
                || "try".equals(beforeParen)
                || "synchronized".equals(beforeParen);
    }

    private int findMatchingParen(String source, int openParen) {
        if (openParen < 0 || openParen >= source.length() || source.charAt(openParen) != '(') {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;
        for (int i = openParen; i < source.length(); i++) {
            char current = source.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '\'') {
                inChar = true;
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
                if (depth < 0) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private int findMatchingBrace(String source, int openBrace) {
        if (openBrace < 0 || openBrace >= source.length() || source.charAt(openBrace) != '{') {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;
        for (int i = openBrace; i < source.length(); i++) {
            char current = source.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '\'') {
                inChar = true;
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
                if (depth < 0) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private String removeUnreachableBreakAfterInfiniteLoop(String source) {
        String[] lines = source.split("\\n", -1);
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < lines.length; i++) {
            if (isBreakAfterInfiniteLoop(lines, i)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private boolean isBreakAfterInfiniteLoop(String[] lines, int index) {
        if (index <= 0 || !"break;".equals(lines[index].trim())) {
            return false;
        }
        int depth = 0;
        boolean sawReturnOrThrow = false;
        for (int i = index - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            depth += count(trimmed, '}');
            depth -= count(trimmed, '{');
            if (trimmed.startsWith("return ") || trimmed.startsWith("throw ")) {
                sawReturnOrThrow = true;
            }
            if (trimmed.startsWith("while (true)") && depth == 0) {
                return sawReturnOrThrow;
            }
            if (depth < 0) {
                return false;
            }
        }
        return false;
    }

    private int count(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }
}
