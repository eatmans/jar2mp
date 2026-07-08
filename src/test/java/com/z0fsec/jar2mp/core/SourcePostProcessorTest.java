package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourcePostProcessorTest {

    @Test
    void removesBroadObjectCasts() {
        String processed = new SourcePostProcessor().process(
                "redirectAttributes.addFlashAttribute(\"message\", (Object)\"ok\");\n"
                        + "repository.save((Object)owner);\n");

        assertFalse(processed.contains("(Object)"));
        assertTrue(processed.contains("repository.save(owner);"));
    }

    @Test
    void removesRedundantLiteralCasts() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Object value) {\n"
                        + "        DateFormatUtils.formatDate(currentDate, (String)\"yyyy-MM-dd\");\n"
                        + "        TimeSplitter.splitTimeRange(startTime, endTime, (int)7);\n"
                        + "        NonBlockingLockUtil.lockAndExecute(key, (long)10L, supplier);\n"
                        + "        accept((String)value);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("DateFormatUtils.formatDate(currentDate, \"yyyy-MM-dd\");"));
        assertTrue(processed.contains("TimeSplitter.splitTimeRange(startTime, endTime, 7);"));
        assertTrue(processed.contains("NonBlockingLockUtil.lockAndExecute(key, 10L, supplier);"));
        assertTrue(processed.contains("accept((String)value);"));
    }

    @Test
    void removesRedundantCastsFromKnownLocalAndParameterTypes() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    public void run(String param, RedissonClient redissonClient, "
                        + "Map<String, List<Field>> logFieldCache) {\n"
                        + "        List<?> nestedList = this.load();\n"
                        + "        accept((String)param, (RedissonClient)redissonClient, "
                        + "(Map)logFieldCache, (List)nestedList);\n"
                        + "        Object raw = param;\n"
                        + "        acceptString((String)raw);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("accept(param, redissonClient, logFieldCache, nestedList);"));
        assertTrue(processed.contains("acceptString((String)raw);"));
    }

    @Test
    void removesRedundantThrowableCastsFromCatchParameters() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    public void run(Object raw) {\n"
                        + "        try {\n"
                        + "            work();\n"
                        + "        } catch (Exception e) {\n"
                        + "            log.error(ExceptionUtil.stacktraceToString((Throwable)e));\n"
                        + "        } catch (Throwable t) {\n"
                        + "            log.error(ExceptionUtil.stacktraceToString((Throwable)t));\n"
                        + "        }\n"
                        + "        log.error(ExceptionUtil.stacktraceToString((Throwable)raw));\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("ExceptionUtil.stacktraceToString(e)"));
        assertTrue(processed.contains("ExceptionUtil.stacktraceToString(t)"));
        assertTrue(processed.contains("ExceptionUtil.stacktraceToString((Throwable)raw)"));
    }

    @Test
    void removesRedundantThrowableCastsFromInitializerCatchParameters() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    static {\n"
                        + "        try {\n"
                        + "            work();\n"
                        + "        } catch (Exception e) {\n"
                        + "            log.error(\"Failed\", (Throwable)e);\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("log.error(\"Failed\", e);"));
        assertFalse(processed.contains("(Throwable)e"));
    }

    @Test
    void removesRedundantThrowableCastsFromKnownExceptionParameters() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    private void handle(Exception e, Object raw) {\n"
                        + "        log.error(\"Failed {}\", (Throwable)e);\n"
                        + "        log.error(\"Raw {}\", (Throwable)raw);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("log.error(\"Failed {}\", e);"));
        assertTrue(processed.contains("log.error(\"Raw {}\", (Throwable)raw);"));
    }

    @Test
    void bridgesTypedListArgumentsForRocketMqBatchSyncSend() {
        String processed = new SourcePostProcessor().process(
                "package demo;\n\n"
                        + "import java.util.List;\n\n"
                        + "class Sample {\n"
                        + "    public void clean(RocketMqSendService rocketMqSendService, List<Long> uidList) {\n"
                        + "        rocketMqSendService.batchSyncSend(\"TOPIC\", uidList, 10);\n"
                        + "        rocketMqSendService.batchSyncSend(\"TOPIC\", new ArrayList<Object>(uidList), 10);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("import java.util.ArrayList;"));
        assertTrue(processed.contains(
                "rocketMqSendService.batchSyncSend(\"TOPIC\", new ArrayList<Object>(uidList), 10);"));
        assertFalse(processed.contains("batchSyncSend(\"TOPIC\", uidList, 10)"));
    }

    @Test
    void restoresRawArrayListTypesFromTypedListMethodArguments() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    public void run(List<User> userList) {\n"
                        + "        ArrayList disableFailUserIdList = Lists.newArrayList();\n"
                        + "        ArrayList removeUidList = new ArrayList();\n"
                        + "        this.disableUser(((User)userList.get(1)).getUid(), (List)disableFailUserIdList);\n"
                        + "        this.removeUsers((List)removeUidList);\n"
                        + "    }\n"
                        + "    private void disableUser(Long uid, List<Long> disableFailUserIdList) {\n"
                        + "    }\n"
                        + "    private void removeUsers(List<Long> removeUidList) {\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("List<Long> disableFailUserIdList = Lists.newArrayList();"));
        assertTrue(processed.contains("List<Long> removeUidList = new ArrayList<>();"));
        assertTrue(processed.contains(
                "this.disableUser(((User)userList.get(1)).getUid(), disableFailUserIdList);"));
        assertTrue(processed.contains("this.removeUsers(removeUidList);"));
    }

    @Test
    void restoresRawCollectionTypesFromBytecodeLocalGenerics() {
        Map<String, String> localTypes = new java.util.LinkedHashMap<>();
        localTypes.put("disableFailUserIdList", "java.util.List<java.lang.Long>");
        localTypes.put("uidLabelSet", "java.util.Set<java.lang.Long>");
        localTypes.put("balanceExcels", "java.util.ArrayList<com.example.domain.dto.LabelCoinBalanceExcel>");
        localTypes.put("pageInfo",
                "com.ochat.framework.common.pojo.vo.PageInfo<com.ochat.activity.pojo.resp.ActivityPageForWebResp>");

        String processed = new SourcePostProcessor().process(
                "package com.example.biz;\n\n"
                        + "import com.example.domain.dto.LabelCoinBalanceExcel;\n"
                        + "import com.ochat.framework.common.pojo.vo.PageInfo;\n"
                        + "import java.util.ArrayList;\n"
                        + "import java.util.List;\n"
                        + "import java.util.Set;\n\n"
                        + "class Sample {\n"
                        + "    public void run() {\n"
                        + "        ArrayList disableFailUserIdList = Lists.newArrayList();\n"
                        + "        Set uidLabelSet = this.detailService.queryByLabelName(labelName);\n"
                        + "        ArrayList balanceExcels = new ArrayList();\n"
                        + "        PageInfo pageInfo = this.activityApi.pageActivity(req);\n"
                        + "    }\n"
                        + "}\n",
                null,
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(),
                localTypes);

        assertTrue(processed.contains("List<Long> disableFailUserIdList = Lists.newArrayList();"));
        assertTrue(processed.contains("Set<Long> uidLabelSet = this.detailService.queryByLabelName(labelName);"));
        assertTrue(processed.contains(
                "ArrayList<LabelCoinBalanceExcel> balanceExcels = new ArrayList<>();"));
        assertTrue(processed.contains("import com.ochat.activity.pojo.resp.ActivityPageForWebResp;"));
        assertTrue(processed.contains(
                "PageInfo<ActivityPageForWebResp> pageInfo = this.activityApi.pageActivity(req);"));
    }

    @Test
    void avoidsImportsForConflictingBytecodeLocalGenericSimpleNames() {
        Map<String, String> localTypes = new java.util.LinkedHashMap<>();
        localTypes.put("switches", "java.util.List<com.example.share.dto.UserSubSwitchDTO>");

        String processed = new SourcePostProcessor().process(
                "package com.example.mapstruct;\n\n"
                        + "import com.ochat.global.config.pojo.resp.UserSubSwitchDTO;\n"
                        + "import java.util.List;\n\n"
                        + "class Sample {\n"
                        + "    public void run() {\n"
                        + "        List switches = this.load();\n"
                        + "    }\n"
                        + "}\n",
                null,
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(),
                localTypes);

        assertFalse(processed.contains("import com.example.share.dto.UserSubSwitchDTO;"));
        assertTrue(processed.contains(
                "List<com.example.share.dto.UserSubSwitchDTO> switches = this.load();"));
    }

    @Test
    void removesCollectionCastsFromCollectionUtilityCalls() {
        String processed = new SourcePostProcessor().process(
                "if (CollectionUtils.isEmpty((Collection)list)) return;\n"
                        + "if (CollectionUtils.isNotEmpty((Collection)(rows = service.list()))) run();\n"
                        + "if (CollUtil.isEmpty((java.util.Collection)ids)) return;\n"
                        + "writer.write((Collection)rows, sheet);\n");

        assertTrue(processed.contains("CollectionUtils.isEmpty(list)"));
        assertTrue(processed.contains("CollectionUtils.isNotEmpty((rows = service.list()))"));
        assertTrue(processed.contains("CollUtil.isEmpty(ids)"));
        assertTrue(processed.contains("writer.write((Collection)rows, sheet);"));
    }

    @Test
    void restoresLettuceTimeoutAutounboxing() {
        String processed = new SourcePostProcessor().process(
                "private LettuceConnectionFactory getConnectionFactory(Tuple6 redisConfig) {\n"
                        + "    Long timeout = (Long)redisConfig._2();\n"
                        + "    LettuceConnectionFactory connectionFactory = this.lettuceConnectionFactory("
                        + "standaloneConfiguration, genericObjectPoolConfig, timeout.longValue());\n"
                        + "    return connectionFactory;\n"
                        + "}\n"
                        + "private LettuceConnectionFactory lettuceConnectionFactory("
                        + "RedisStandaloneConfiguration standaloneConfiguration, "
                        + "GenericObjectPoolConfig genericObjectPoolConfig, long timeout) {\n"
                        + "    return null;\n"
                        + "}\n");

        assertTrue(processed.contains("standaloneConfiguration, genericObjectPoolConfig, timeout);"));
        assertFalse(processed.contains("timeout.longValue()"));
    }

    @Test
    void restoresSameClassStaticFinalStringConstantReferences() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    private static final String PREFIX = \"non_blocking_lock:\";\n"
                        + "    private static final String LOCK_VALUE = \"locked\";\n"
                        + "    void run(String key, RedisTemplate redisTemplate) {\n"
                        + "        String lockKey = \"non_blocking_lock:\".concat(key);\n"
                        + "        redisTemplate.opsForValue().setIfAbsent(lockKey, \"locked\", 10L, TimeUnit.SECONDS);\n"
                        + "        log.info(\"other\");\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("private static final String PREFIX = \"non_blocking_lock:\";"));
        assertTrue(processed.contains("private static final String LOCK_VALUE = \"locked\";"));
        assertTrue(processed.contains("String lockKey = PREFIX.concat(key);"));
        assertTrue(processed.contains("setIfAbsent(lockKey, LOCK_VALUE, 10L, TimeUnit.SECONDS);"));
        assertTrue(processed.contains("log.info(\"other\");"));
    }

    @Test
    void restoresPrintableUnicodeEscapesInsideLiterals() {
        String processed = new SourcePostProcessor().process(
                "log.info(\"" + "\\u8bf7" + "\\u6c42" + "\\u6765" + "\\u6e90" + "[{}]\");\n"
                        + "String marker = \"" + "\\u0001" + "\";\n");

        assertTrue(processed.contains("\"" + "\u8bf7\u6c42\u6765\u6e90" + "[{}]\""));
        assertTrue(processed.contains("\"" + "\\u0001" + "\""));
        assertFalse(processed.contains("\\u8bf7"));
    }

    @Test
    void restoresSwitchBraceSpacing() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void convert(String value) {\n"
                        + "        switch (Status.byName((String)value)){\n"
                        + "            case WAIT: return;\n"
                        + "        }\n"
                        + "        String literal = \"switch (x){\";\n"
                        + "    }\n"
                        + "    int type(Mode mode) {\n"
                        + "        return switch (mode){\n"
                        + "            case A -> 1;\n"
                        + "        };\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("switch (Status.byName((String)value)) {"));
        assertTrue(processed.contains("return switch (mode) {"));
        assertTrue(processed.contains("String literal = \"switch (x){\";"));
    }

    @Test
    void restoresUnindentedMemberMethodDeclarations() {
        String processed = new SourcePostProcessor().process(
                "public class Sample {\n"
                        + "    private static final String VALUE = \"x\";\n"
                        + "public static <T> T lockAndExecute(String key, Supplier<T> supplier) {\n"
                        + "        return supplier.get();\n"
                        + "    }\n"
                        + "private int sendImageCheckRequest(String url) throws Exception {\n"
                        + "        return 200;\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("public class Sample {"));
        assertTrue(processed.contains("    public static <T> T lockAndExecute"));
        assertTrue(processed.contains("    private int sendImageCheckRequest"));
    }

    @Test
    void removesWholeClassAnalysisDiagnosticComments() {
        String processed = new SourcePostProcessor().process(
                "package demo;\n\n"
                        + "/*\n"
                        + " * Exception performing whole class analysis ignored.\n"
                        + " */\n"
                        + "public class Sample {\n"
                        + "}\n");

        assertFalse(processed.contains("Exception performing whole class analysis ignored"));
        assertTrue(processed.contains("public class Sample"));
    }

    @Test
    void stripsDecompilerHeader() {
        String processed = new SourcePostProcessor().process(
                "/*\n"
                        + " * Decompiled with CFR 0.152.\n"
                        + " * \n"
                        + " * Could not load the following classes:\n"
                        + " *  demo.Dependency\n"
                        + " */\n"
                        + "package demo;\n\npublic class Sample {}\n",
                "demo.Sample");

        assertFalse(processed.contains("Decompiled with"));
        assertTrue(processed.startsWith("package demo;"));
    }

    @Test
    void stripsOnlyDecompilerHeaderAndPreservesPackageImports() {
        String processed = new SourcePostProcessor().process(
                "/*\n"
                        + " * Decompiled with CFR.\n"
                        + " */\n"
                        + "package demo;\n\n"
                        + "import java.util.List;\n\n"
                        + "/*\n"
                        + " * This class specifies class file version 49.0.\n"
                        + " */\n"
                        + "public class Sample { List<String> values; }\n",
                "demo.Sample");

        assertTrue(processed.startsWith("package demo;"));
        assertTrue(processed.contains("import java.util.List;"));
        assertTrue(processed.contains("public class Sample"));
    }

    @Test
    void removesImplicitImports() {
        String processed = new SourcePostProcessor().process(
                "package demo;\n\n"
                        + "import demo.Helper;\n"
                        + "import java.lang.String;\n"
                        + "import demo.nested.Other;\n"
                        + "import java.util.List;\n\n"
                        + "public class Sample {}\n",
                "demo.Sample");

        assertFalse(processed.contains("import demo.Helper;"));
        assertFalse(processed.contains("import java.lang.String;"));
        assertTrue(processed.contains("import demo.nested.Other;"));
        assertTrue(processed.contains("import java.util.List;"));
    }

    @Test
    void removesParameterArrayCasts() {
        String processed = new SourcePostProcessor().process(
                "public static void main(String[] args) {\n"
                        + "        run((String[])args);\n"
                        + "}\n");

        assertTrue(processed.contains("run(args);"));
        assertFalse(processed.contains("(String[])args"));
    }

    @Test
    void removesRepeatedValidationAnnotationsOnOneParameter() {
        String processed = new SourcePostProcessor().process(
                "public Result<UserBankCardDTO> queryById(@PathVariable(value=\"id\") "
                        + "@Valid @NotNull(message=\"id cannot be empty\") "
                        + "@Valid @NotNull(message=\"id cannot be empty\") Long id) {\n"
                        + "    return Result.ok(id);\n"
                        + "}\n");

        assertTrue(processed.contains("@Valid @NotNull(message=\"id cannot be empty\") Long id"));
        assertFalse(processed.contains("@Valid @NotNull(message=\"id cannot be empty\") @Valid"));
    }

    @Test
    void restoresLambdaQueryWrapperEntityTypeFromMethodReferences() {
        String processed = new SourcePostProcessor().process(
                "LambdaQueryWrapper queryWrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)new "
                        + "LambdaQueryWrapper().eq(User::getUid, uid)).orderByAsc(User::getUid);\n"
                        + "List list = this.userService.list((Wrapper)queryWrapper);\n"
                        + "this.userService.clean(list.stream().map(User::getUid).collect(Collectors.toSet()));\n");

        assertTrue(processed.contains("LambdaQueryWrapper<User> queryWrapper"));
        assertTrue(processed.contains("(LambdaQueryWrapper<User>)new LambdaQueryWrapper<User>()"));
        assertTrue(processed.contains("List<User> list = this.userService.list((Wrapper)queryWrapper);"));
    }

    @Test
    void restoresLambdaQueryWrapperDeclarationFromLaterWrapperMethodReference() {
        String processed = new SourcePostProcessor().process(
                "LambdaQueryWrapper wrapper = new LambdaQueryWrapper();\n"
                        + "((LambdaQueryWrapper<UserVerityAudit>)((LambdaQueryWrapper<UserVerityAudit>)"
                        + "wrapper.eq(UserVerityAudit::getUid, uid)).eq(UserVerityAudit::getStatus, 0))"
                        + ".last(\"limit 1\");\n");

        assertTrue(processed.contains("LambdaQueryWrapper<UserVerityAudit> wrapper = "
                + "new LambdaQueryWrapper<UserVerityAudit>();"));
    }

    @Test
    void restoresCfrLambdaMetafactoryMethodReferences() {
        String processed = new SourcePostProcessor().process(
                "LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)new LambdaQueryWrapper()"
                        + ".isNotNull((SFunction & Serializable)LambdaMetafactory.altMetafactory(null, null, "
                        + "null, (Ljava/lang/Object;)Ljava/lang/Object;, getIp(), "
                        + "(Lcom/example/domain/entity/AuditLog;)Ljava/lang/Object;)()))"
                        + ".last(\" limit \" + num + \",\" + limitCount);\n"
                        + "log.error(\"id:{}\", list.stream().map((Function<AuditLog, Long>)"
                        + "LambdaMetafactory.metafactory(null, null, null, "
                        + "(Ljava/lang/Object;)Ljava/lang/Object;, getAuditLogId(), "
                        + "(Lcom/example/domain/entity/AuditLog;)Ljava/lang/Long;)())"
                        + ".collect(Collectors.toList()));\n");

        assertTrue(processed.contains("new LambdaQueryWrapper<AuditLog>().isNotNull(AuditLog::getIp)"));
        assertTrue(processed.contains("list.stream().map(AuditLog::getAuditLogId)"));
        assertFalse(processed.contains("LambdaMetafactory"));
    }

    @Test
    void restoresCfrMethodOpeningImplicitLocalDeclarations() {
        String processed = new SourcePostProcessor().process(
                "public void cryptLoginLogHistorical(int limitCount, Boolean execOnce, Boolean onlyQuery) {\n"
                        + "        stopWatch = new StopWatch(\"cryptLoginLogHistorical\");\n"
                        + "        stopWatch.start();\n"
                        + "        batch = 0;\n"
                        + "        count = 0;\n"
                        + "        num = 0;\n"
                        + "        try {\n"
                        + "            CryptContext.enableCrypt();\n"
                        + "            wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)new LambdaQueryWrapper()"
                        + ".isNotNull(LoginLog::getIp)).last(\" limit \" + num + \",\" + limitCount);\n"
                        + "            list = this.loginLogService.list((Wrapper)wrapper);\n"
                        + "            if (execOnce.booleanValue() && batch > 0) ** break;\n"
                        + "        }\n"
                        + "        catch (Throwable t) {\n"
                        + "            stopWatch.stop();\n"
                        + "            throw t;\n"
                        + "        }\n"
                        + "}\n");

        assertTrue(processed.contains("StopWatch stopWatch = new StopWatch(\"cryptLoginLogHistorical\");"));
        assertTrue(processed.contains("int batch = 0;"));
        assertTrue(processed.contains("int count = 0;"));
        assertTrue(processed.contains("int num = 0;"));
        assertTrue(processed.contains("LambdaQueryWrapper<LoginLog> wrapper = "
                + "(LambdaQueryWrapper<LoginLog>)((LambdaQueryWrapper<LoginLog>)new "
                + "LambdaQueryWrapper<LoginLog>().isNotNull(LoginLog::getIp))"));
        assertTrue(processed.contains("List<LoginLog> list = this.loginLogService.list((Wrapper)wrapper);"));
        assertTrue(processed.contains("if (execOnce.booleanValue() && batch > 0) break;"));
        assertFalse(processed.contains("** break"));
    }

    @Test
    void restoresServiceListTypeFromBatchUpdateUsage() {
        String processed = new SourcePostProcessor().process(
                "public void cryptAdvanceReviewHistorical(Integer limitCount) {\n"
                        + "    LambdaQueryWrapper wrapper = (LambdaQueryWrapper)new LambdaQueryWrapper()"
                        + ".last(\" limit 0,\" + limitCount);\n"
                        + "    List<BaseReview> list = this.advanceReviewService.list((Wrapper)wrapper);\n"
                        + "    count += this.advanceReviewService.batchUpdateCryptById(list).intValue();\n"
                        + "    log.error(\"id:{}\", list.stream().map(BaseReview::getId)"
                        + ".collect(Collectors.toList()));\n"
                        + "}\n");

        assertTrue(processed.contains("LambdaQueryWrapper<AdvanceReview> wrapper = "
                + "(LambdaQueryWrapper)new LambdaQueryWrapper<AdvanceReview>()"));
        assertTrue(processed.contains("List<AdvanceReview> list = "
                + "this.advanceReviewService.list((Wrapper)wrapper);"));
        assertTrue(processed.contains("list.stream().map(AdvanceReview::getId)"));
        assertFalse(processed.contains("List<BaseReview> list = this.advanceReviewService"));
    }

    @Test
    void restoresServiceReturnedListTypeFromBatchUpdateUsage() {
        String processed = new SourcePostProcessor().process(
                "package com.example.task;\n\n"
                        + "import com.example.domain.entity.BaseReview;\n"
                        + "import com.example.service.SysUserService;\n\n"
                        + "public void cryptTUserHistorical() {\n"
                        + "    List list = this.sysUserService.getCryptTUserHistorical(LocalDateTime.now(), "
                        + "\"nlocal\");\n"
                        + "    list = list.stream().peek(u -> ClassReflectUtil.displaceFields(u, true, "
                        + "CRYPT_ANNOTATIONS)).collect(Collectors.toList());\n"
                        + "    rsCount = this.sysUserService.batchUpdateCryptById(list);\n"
                        + "}\n");

        assertTrue(processed.contains("import com.example.domain.entity.SysUser;"));
        assertTrue(processed.contains("List<SysUser> list = "
                + "this.sysUserService.getCryptTUserHistorical(LocalDateTime.now(), \"nlocal\");"));
    }

    @Test
    void doesNotRedeclareExistingRawListFromWrapperAssignment() {
        String processed = new SourcePostProcessor().process(
                "public void emailSuffixMatchesUser() {\n"
                        + "    LambdaQueryWrapper queryWrapper = (LambdaQueryWrapper)new LambdaQueryWrapper()"
                        + ".gt(Account::getUid, startUid);\n"
                        + "    List accountList = new ArrayList();\n"
                        + "    try {\n"
                        + "        accountList = this.accountService.list((Wrapper)queryWrapper);\n"
                        + "    }\n"
                        + "    catch (Exception e) {\n"
                        + "    }\n"
                        + "    for (Account account : accountList) {\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("List<Account> accountList = new ArrayList<>();"));
        assertTrue(processed.contains("accountList = this.accountService.list((Wrapper)queryWrapper);"));
        assertFalse(processed.contains("List<Account> accountList = this.accountService.list"));
    }

    @Test
    void restoresLambdaUpdateWrapperEntityTypeFromMethodReferences() {
        String processed = new SourcePostProcessor().process(
                "this.sysUserMapper.update(null, (Wrapper)((LambdaUpdateWrapper)new LambdaUpdateWrapper()"
                        + ".eq(SysUser::getUserId, req.getUserId())).set(SysUser::getDeviceLimitEnabled, "
                        + "req.getDeviceLimitEnabled()));\n");

        assertTrue(processed.contains("(LambdaUpdateWrapper<SysUser>)new LambdaUpdateWrapper<SysUser>()"));
    }

    @Test
    void restoresLambdaUpdateChainWrapperEntityTypeFromMethodReferences() {
        String processed = new SourcePostProcessor().process(
                "((LambdaUpdateChainWrapper)((LambdaUpdateChainWrapper)this.userCoinRecordService.lambdaUpdate()"
                        + ".set(UserCoinRecord::getUid, financeUid)).in(UserCoinRecord::getTargetId, "
                        + "sourceRedPacketIds)).update();\n");

        assertTrue(processed.contains("(LambdaUpdateChainWrapper<UserCoinRecord>)this.userCoinRecordService"
                + ".lambdaUpdate()"));
        assertFalse(processed.contains("(LambdaUpdateChainWrapper)this.userCoinRecordService.lambdaUpdate()"));
    }

    @Test
    void unwrapsSingleElementRedisSetOperationArrays() {
        String processed = new SourcePostProcessor().process(
                "userTemplate.opsForSet().add(\"user:change:device:switch\", "
                        + "(Object[])new String[]{UserChangeDeviceSwitchType.getCode((String)parentCode).getCode()});\n"
                        + "userTemplate.opsForSet().remove(\"user:change:device:switch\", "
                        + "new Object[]{UserChangeDeviceSwitchType.getCode((String)parentCode).getCode()});\n");

        assertTrue(processed.contains("opsForSet().add(\"user:change:device:switch\", "
                + "UserChangeDeviceSwitchType.getCode((String)parentCode).getCode())"));
        assertTrue(processed.contains("opsForSet().remove(\"user:change:device:switch\", "
                + "UserChangeDeviceSwitchType.getCode((String)parentCode).getCode())"));
        assertFalse(processed.contains("new String[]"));
        assertFalse(processed.contains("new Object[]"));
    }

    @Test
    void removesSyntheticOuterConstructorArgumentWhenNoArgConstructorExists() {
        String processed = new SourcePostProcessor().process(
                "public class RestTemplateConfig {\n"
                        + "    void run() { call(new RestTemplateConfig.HeadParamInterceptor(this)); }\n"
                        + "    private class HeadParamInterceptor {\n"
                        + "        private HeadParamInterceptor() {\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("new HeadParamInterceptor()"));
        assertFalse(processed.contains("new HeadParamInterceptor(this)"));
    }

    @Test
    void scopesLambdaQueryWrapperTypesForRepeatedVariableNames() {
        String processed = new SourcePostProcessor().process(
                "while (true) {\n"
                        + "    LambdaQueryWrapper queryWrapper;\n"
                        + "    List userList;\n"
                        + "    if (empty((Collection)(userList = this.userService.list((Wrapper)(queryWrapper = "
                        + "(LambdaQueryWrapper)new LambdaQueryWrapper().gt(User::getUid, startUid))))) break;\n"
                        + "}\n"
                        + "while (true) {\n"
                        + "    LambdaQueryWrapper queryWrapper;\n"
                        + "    List descriptionList;\n"
                        + "    if (empty((Collection)(descriptionList = this.userDescriptionService.list((Wrapper)(queryWrapper = "
                        + "(LambdaQueryWrapper)new LambdaQueryWrapper().gt(UserDescription::getId, startId))))) break;\n"
                        + "}\n");

        assertTrue(processed.contains("LambdaQueryWrapper<User> queryWrapper;"));
        assertTrue(processed.contains("List<User> userList;"));
        assertTrue(processed.contains("LambdaQueryWrapper<UserDescription> queryWrapper;"));
        assertTrue(processed.contains("List<UserDescription> descriptionList;"));
        assertFalse(processed.contains("LambdaQueryWrapper<UserDescription> queryWrapper;\n"
                + "    List<UserDescription> userList;"));
    }

    @Test
    void restoresPageInfoElementTypeFromRowListStreamMethodReference() {
        String processed = new SourcePostProcessor().process(
                "PageInfo resp;\n"
                        + "if (!empty((resp = this.api.query(req)).getRowList())) {\n"
                        + "    List uids = resp.getRowList().stream().map(AdOrderPageResp::getUid).collect(Collectors.toList());\n"
                        + "}\n");

        assertTrue(processed.contains("PageInfo<AdOrderPageResp> resp;"));
    }

    @Test
    void restoresPageInfoElementTypeFromRowListCast() {
        String processed = new SourcePostProcessor().process(
                "public PageData<RedPacketGameStatisticsDTO> getGameStatistics(GameStatisticsReq req) {\n"
                        + "    PageInfo resp = this.redPacketGameStatisticsApi.getGameStatistics(req);\n"
                        + "    List result = mapper.toDTO(((GameStatisticsPageResp)resp.getRowList().getFirst()).getRespList());\n"
                        + "    return new PageData(result, resp.getTotal());\n"
                        + "}\n");

        assertTrue(processed.contains("PageInfo<GameStatisticsPageResp> resp ="));
        assertFalse(processed.contains("PageInfo<RedPacketGameStatisticsDTO> resp"));
    }

    @Test
    void removesRedundantPageInfoRowListElementCasts() {
        String processed = new SourcePostProcessor().process(
                "public PageData<RedPacketGameStatisticsDTO> getGameStatistics(GameStatisticsReq req) {\n"
                        + "    PageInfo resp = this.redPacketGameStatisticsApi.getGameStatistics(req);\n"
                        + "    List result = mapper.toDTO(((GameStatisticsPageResp)resp.getRowList().getFirst()).getRespList());\n"
                        + "    return new PageData(result, resp.getTotal().longValue(), "
                        + "(long)resp.getPageNum().intValue(), (long)resp.getPageSize().intValue(), "
                        + "(long)resp.getPages(), ((GameStatisticsPageResp)resp.getRowList().getFirst()).getSummaryResp());\n"
                        + "}\n");

        assertTrue(processed.contains("PageInfo<GameStatisticsPageResp> resp ="));
        assertTrue(processed.contains("mapper.toDTO(resp.getRowList().getFirst().getRespList())"));
        assertTrue(processed.contains("resp.getPages(), resp.getRowList().getFirst().getSummaryResp());"));
        assertFalse(processed.contains("((GameStatisticsPageResp)resp.getRowList().getFirst())"));
    }

    @Test
    void ignoresCollectionCastsWhenInferringPageInfoElementType() {
        String processed = new SourcePostProcessor().process(
                "public PageData<ChannelPageForWebResp> page(ChannelReq req) {\n"
                        + "    PageInfo resp = this.channelApi.page(req);\n"
                        + "    if (CollectionUtils.isEmpty((Collection)resp.getRowList())) return new PageData();\n"
                        + "    return new PageData(resp.getRowList(), resp.getTotal());\n"
                        + "}\n");

        assertFalse(processed.contains("PageInfo<Collection> resp"));
    }

    @Test
    void restoresPageDataReturnConstructorSyntax() {
        String processed = new SourcePostProcessor().process(
                "public PageData<DeviceBlacklistDTO> queryByPage(QueryRequest req) {\n"
                        + "    PageInfo<DeviceBlacklistDTO> resp = this.api.query(req);\n"
                        + "    return new PageData(resp.getRowList(), resp.getTotal().longValue(), "
                        + "(long)resp.getPageNum().intValue(), (long)resp.getPageSize().intValue());\n"
                        + "}\n"
                        + "public PageData<DeviceBlacklistDTO> querySummary(QueryRequest req) {\n"
                        + "    PageInfo<DeviceBlacklistDTO> resp = this.api.query(req);\n"
                        + "    return new PageData(resp.getRowList(), resp.getTotal().longValue(), "
                        + "(long)resp.getPageNum().intValue(), (long)resp.getPageSize().intValue(), "
                        + "(long)resp.getPages(), resp.getSummary());\n"
                        + "}\n"
                        + "public Object rawPage(QueryRequest req) {\n"
                        + "    return new PageData(resp.getRowList(), (long)resp.getPageNum().intValue());\n"
                        + "}\n");

        assertTrue(processed.contains("return new PageData<>(resp.getRowList(), resp.getTotal().longValue(), "
                + "resp.getPageNum().intValue(), resp.getPageSize().intValue());"));
        assertTrue(processed.contains("return new PageData(resp.getRowList(), resp.getTotal().longValue(), "
                + "resp.getPageNum().intValue(), resp.getPageSize().intValue(), "
                + "resp.getPages(), resp.getSummary());"));
        assertTrue(processed.contains("return new PageData(resp.getRowList(), (long)resp.getPageNum().intValue());"));
    }

    @Test
    void keepsSixArgumentPageDataConstructorsRawWhenTrailingArgumentStartsWithCast() {
        String processed = new SourcePostProcessor().process(
                "public PageData<RedPacketGameStatisticsDTO> getGameStatistics(GameStatisticsReq req) {\n"
                        + "    PageInfo<GameStatisticsPageResp> resp = this.api.getGameStatistics(req);\n"
                        + "    List<RedPacketGameStatisticsDTO> result = mapper.toDTO("
                        + "((GameStatisticsPageResp)resp.getRowList().getFirst()).getRespList());\n"
                        + "    return new PageData(result, resp.getTotal().longValue(), "
                        + "resp.getPageNum().intValue(), resp.getPageSize().intValue(), "
                        + "resp.getPages(), ((GameStatisticsPageResp)resp.getRowList().getFirst()).getSummaryResp());\n"
                        + "}\n");

        assertTrue(processed.contains("return new PageData(result, resp.getTotal().longValue(), "
                + "resp.getPageNum().intValue(), resp.getPageSize().intValue(), "
                + "resp.getPages(), resp.getRowList().getFirst().getSummaryResp());"));
        assertFalse(processed.contains("new PageData<>(result, resp.getTotal().longValue(), "
                + "resp.getPageNum().intValue(), resp.getPageSize().intValue(), resp.getPages()"));
    }

    @Test
    void scopesPageInfoElementTypesForRepeatedLocalNames() {
        String processed = new SourcePostProcessor().process(
                "public PageData<BuyOrderPageResp> buyPage() {\n"
                        + "    PageInfo resp;\n"
                        + "    if (!empty((resp = api.buy(req)).getRowList())) {\n"
                        + "        List uids = resp.getRowList().stream().map(BuyOrderPageResp::getUid).toList();\n"
                        + "    }\n"
                        + "    return new PageData(resp.getRowList(), resp.getTotal());\n"
                        + "}\n"
                        + "public PageData<SellOrderInnerPageResp> sellPage() {\n"
                        + "    PageInfo resp;\n"
                        + "    if (!empty((resp = api.sell(req)).getRowList())) {\n"
                        + "        List uids = resp.getRowList().stream().map(SellOrderInnerPageResp::getUid).toList();\n"
                        + "    }\n"
                        + "    return new PageData(resp.getRowList(), resp.getTotal());\n"
                        + "}\n");

        assertTrue(processed.contains("PageInfo<BuyOrderPageResp> resp;"));
        assertTrue(processed.contains("PageInfo<SellOrderInnerPageResp> resp;"));
    }

    @Test
    void restoresLocalTypeFromGenericMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "public void run(Long id) {\n"
                        + "    Map userMap = this.getUserMap(null, Collections.singletonList(id));\n"
                        + "    String account = userMap.getOrDefault(id, \"\");\n"
                        + "}\n"
                        + "private Map<Long, String> getUserMap(User user, List<Long> uidList) {\n"
                        + "    return Maps.newHashMap();\n"
                        + "}\n");

        assertTrue(processed.contains("Map<Long, String> userMap = this.getUserMap"));
    }

    @Test
    void restoresPageDataAndListTypesFromMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "public Result<PageData<GroupsMemberListDTO>> pageInfo() {\n"
                        + "    PageData pageData = this.groupMemberService.queryByPage(req, pageRequest);\n"
                        + "    List groupsMemberListDTOList = pageData.getList();\n"
                        + "    List uids = groupsMemberListDTOList.stream().map(GroupsMemberListDTO::getUid).collect(Collectors.toList());\n"
                        + "    return this.handleResult(pageData);\n"
                        + "}\n");

        assertTrue(processed.contains("PageData<GroupsMemberListDTO> pageData = "
                + "this.groupMemberService.queryByPage(req, pageRequest);"));
        assertFalse(processed.contains("pageData="));
        assertTrue(processed.contains("List<GroupsMemberListDTO> groupsMemberListDTOList = pageData.getList();"));
    }

    @Test
    void leavesPageDataRawWhenMappedToDifferentReturnDto() {
        String processed = new SourcePostProcessor().process(
                "public Result<PageData<GroupsMemberListV2DTO>> getPage() {\n"
                        + "    PageData pageDate = this.groupMemberService.queryByPage(req, pageRequest);\n"
                        + "    return this.handleResult(new PageData(GroupMemberMapstruct.INSTANCE.toList(pageDate.getList()), "
                        + "pageDate.getTotal(), pageDate.getCurrent(), pageDate.getLimit(), pageDate.getPages()));\n"
                        + "}\n");

        assertTrue(processed.contains("PageData pageDate = this.groupMemberService.queryByPage"));
        assertFalse(processed.contains("PageData<GroupsMemberListV2DTO> pageDate"));
    }

    @Test
    void restoresLambdaQueryChainWrapperEntityTypeFromMethodReferences() {
        String processed = new SourcePostProcessor().process(
                "return ((LambdaQueryChainWrapper)((LambdaQueryChainWrapper)this.contactsService.lambdaQuery()"
                        + ".eq(Contacts::getUid, uid)).eq(Contacts::getStatus, 1)).list().stream()"
                        + ".map(Contacts::getFriendUid).collect(Collectors.toSet());\n");

        assertTrue(processed.contains("(LambdaQueryChainWrapper<Contacts>)this.contactsService.lambdaQuery()"));
        assertTrue(processed.contains("(LambdaQueryChainWrapper<Contacts>)((LambdaQueryChainWrapper<Contacts>)"));
    }

    @Test
    void alignsStreamMethodReferenceOwnerWithListElementType() {
        String processed = new SourcePostProcessor().process(
                "List<GroupsMemberListDTO> groupsMemberListDTOList = pageData.getList();\n"
                        + "List uids = groupsMemberListDTOList.stream().map(GroupMember::getUid).collect(Collectors.toList());\n");

        assertTrue(processed.contains("groupsMemberListDTOList.stream().map(GroupsMemberListDTO::getUid)"));
    }

    @Test
    void restoresStringSplitArraysFromCharSequenceArrays() {
        String processed = new SourcePostProcessor().process(
                "CharSequence[] picArray = descriptionPic.split(\",\", -1);\n"
                        + "String oldPic = picArray[i];\n");

        assertTrue(processed.contains("String[] picArray = descriptionPic.split(\",\", -1);"));
    }

    @Test
    void restoresStringListLocalsFromArraysAndGuavaListFactories() {
        String processed = new SourcePostProcessor().process(
                "ArrayList limitsKeys;\n"
                        + "limitsKeys = Arrays.asList(request.getParameter(\"authorityKey\"));\n"
                        + "limitsKeys = Lists.newArrayList((Object[])annotation.value());\n"
                        + "Lists.newArrayList((Iterable)limitsKeys).stream().map(arg -> service.getGoogleVerify(arg));\n");

        assertTrue(processed.contains("java.util.List<String> limitsKeys;"));
        assertTrue(processed.contains("Lists.newArrayList(annotation.value())"));
        assertTrue(processed.contains("Lists.newArrayList(limitsKeys).stream()"));
    }

    @Test
    void addsImmutableMapBuilderTypeArgumentsFromMapReturnType() {
        String processed = new SourcePostProcessor().process(
                "private Map<Long, String> getUserMap(User user, List<Long> uidList) {\n"
                        + "    return Optional.ofNullable(user).map(e -> ImmutableMap.builder()"
                        + ".put(e.getUid(), e.getIdentify()).build()).orElseGet(() -> Maps.newHashMap());\n"
                        + "}\n");

        assertTrue(processed.contains("ImmutableMap.<Long, String>builder()"));
    }

    @Test
    void castsImmutableMapBuilderLambdaToMapReturnType() {
        String processed = new SourcePostProcessor().process(
                "private Map<Long, String> getUserMap(User user, List<Long> uidList) {\n"
                        + "    return Optional.ofNullable(user).map(e -> ImmutableMap.builder()"
                        + ".put(e.getUid(), e.getIdentify()).build()).orElseGet(() -> this.findUserMap(uidList));\n"
                        + "}\n");

        assertTrue(processed.contains("map(e -> (Map<Long, String>)ImmutableMap.<Long, String>builder()"));
    }

    @Test
    void restoresGenericReturnLocalTypeFromQualifiedCall() {
        String processed = new SourcePostProcessor().process(
                "public static void sendStringByUid(Long uid) {\n"
                        + "    Set userSocketSessionAll = WebSocketServer.getWebSocketSessionByUid((Long)uid);\n"
                        + "}\n"
                        + "private static Set<WebSocketSession> getWebSocketSessionByUid(Long uid) {\n"
                        + "    return uidToSessionMap.get(uid);\n"
                        + "}\n");

        assertTrue(processed.contains("Set<WebSocketSession> userSocketSessionAll = "
                + "WebSocketServer.getWebSocketSessionByUid"));
    }

    @Test
    void addsRawCollectionElementTypeWhenEnhancedForUsesTypedElement() {
        String processed = new SourcePostProcessor().process(
                "HashSet sessionsCopy = new HashSet(userSocketSessionAll);\n"
                        + "for (WebSocketSession socketSession : sessionsCopy) {\n"
                        + "}\n");

        assertTrue(processed.contains("HashSet<WebSocketSession> sessionsCopy = new HashSet<WebSocketSession>"));
        assertTrue(processed.contains("for (WebSocketSession socketSession : sessionsCopy)"));
    }

    @Test
    void restoresRawListElementTypeFromStreamMethodReference() {
        String processed = new SourcePostProcessor().process(
                "List list = this.redPacketService.getRedPacketList(req);\n"
                        + "Set sourceRedPacketIds = list.stream().map(RedPacket::getId).collect(Collectors.toSet());\n");

        assertTrue(processed.contains("List<RedPacket> list = this.redPacketService.getRedPacketList(req);"));
    }

    @Test
    void restoresSetElementTypeFromLaterStreamMethodReferenceAssignment() {
        String processed = new SourcePostProcessor().process(
                "Set<Object> exclusivePacketUids = new HashSet();\n"
                        + "exclusivePacketUids = banExclusivePacketGroupList.stream()"
                        + ".map(BanGroupResDTO::getUid).collect(Collectors.toSet());\n"
                        + "BanDTO needBanData = this.getNeedBanData(banGroup, uids, exclusivePacketUids, groupIds);\n");

        assertTrue(processed.contains("Set<Long> exclusivePacketUids = new HashSet<>();"));
    }

    @Test
    void leavesRawSetStreamCollectionUntypedWhenOnlyGetterNameIsAvailable() {
        String processed = new SourcePostProcessor().process(
                "Set sourceRedPacketIds = list.stream().map(RedPacket::getId).collect(Collectors.toSet());\n"
                        + "this.userCoinRecordService.lambdaUpdate().in(UserCoinRecord::getTargetId, "
                        + "sourceRedPacketIds).update();\n");

        assertTrue(processed.contains("Set sourceRedPacketIds = list.stream().map(RedPacket::getId)"
                + ".collect(Collectors.toSet());"));
        assertFalse(processed.contains("Set<Long> sourceRedPacketIds"));
    }

    @Test
    void doesNotInferRawListElementTypeFromLaterMethodWithSameVariableName() {
        String processed = new SourcePostProcessor().process(
                "public boolean removeDuplicateReg() {\n"
                        + "    List list = this.accountService.listDuplicateReg();\n"
                        + "    for (Map accountMap : list) {}\n"
                        + "    return true;\n"
                        + "}\n"
                        + "private void disabledUser(List<User> list) {\n"
                        + "    List uidList = list.stream().map(User::getUid).collect(Collectors.toList());\n"
                        + "}\n");

        assertFalse(processed.contains("List<User> list = this.accountService.listDuplicateReg();"));
    }

    @Test
    void restoresPageInfoLocalTypeFromPageDataMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "public PageData<GroupObserverMemberAuditPageResp> getObserverList() {\n"
                        + "    PageInfo resp = this.groupObserverMemberApi.getObserverList(req);\n"
                        + "    resp.getRowList().forEach(item -> item.getGroupObserverMemberRespList());\n"
                        + "    return new PageData(resp.getRowList(), resp.getTotal());\n"
                        + "}\n");

        assertTrue(processed.contains("PageInfo<GroupObserverMemberAuditPageResp> resp = "
                + "this.groupObserverMemberApi.getObserverList(req);"));
        assertFalse(processed.contains("resp="));
    }

    @Test
    void leavesPageInfoRawWhenRowsAreMappedToReturnDto() {
        String processed = new SourcePostProcessor().process(
                "public PageData<AssistUserResp> assistUserPage() {\n"
                        + "    PageInfo resp = this.assistUserInnerInnerApi.queryPage(req);\n"
                        + "    return new PageData(AssistUserMapstruct.INSTANCE.toResp(resp.getRowList()), "
                        + "resp.getTotal(), resp.getPageNum(), resp.getPageSize());\n"
                        + "}\n");

        assertTrue(processed.contains("PageInfo resp = this.assistUserInnerInnerApi.queryPage(req);"));
        assertFalse(processed.contains("PageInfo<AssistUserResp> resp"));
    }

    @Test
    void restoresListLocalTypeFromListMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "public List<GroupObserverMemberPageResp> getGroupInfoByIds() {\n"
                        + "    List resp = this.groupObserverMemberApi.getGroupInfoByIds(req);\n"
                        + "    resp.forEach(item -> item.setGroupName(item.getGroupName()));\n"
                        + "    return resp;\n"
                        + "}\n");

        assertTrue(processed.contains("List<GroupObserverMemberPageResp> resp"));
    }

    @Test
    void restoresListLocalTypeFromWrappedListMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "public ResultA<List<BatchAddAccDTO>> onMessage(RoleUserCreateReq req) {\n"
                        + "    List list = this.userService.generateAccount(req);\n"
                        + "    list.stream().map(e -> e.getUid()).toList();\n"
                        + "    return ListenerResultUtil.getSuc(list);\n"
                        + "}\n");

        assertTrue(processed.contains("List<BatchAddAccDTO> list = this.userService.generateAccount(req);"));
    }

    @Test
    void restoresNestedListPartitionTypesFromSourceListElementType() {
        String processed = new SourcePostProcessor().process(
                "List<Long> sendUidList = this.getSendUidList(commonNotice);\n"
                        + "List listList = com.google.common.collect.Lists.partition((List)sendUidList, (int)batchSize);\n"
                        + "for (List uidList : listList) {\n"
                        + "    uidList.forEach(uid -> this.userService.getUserLanguage(template, uid));\n"
                        + "}\n");

        assertTrue(processed.contains("List<List<Long>> listList = "
                + "com.google.common.collect.Lists.partition(sendUidList, batchSize);"));
        assertTrue(processed.contains("for (List<Long> uidList : listList)"));
    }

    @Test
    void restoresRawListElementTypeFromCollectorsToMapMethodReference() {
        String processed = new SourcePostProcessor().process(
                "List<Long> uidList = messages.stream().map(GroupMemberLimitMsgResp::getUid).toList();\n"
                        + "List userList = this.userService.listByIds(uidList);\n"
                        + "Map<Long, Integer> lastChannelByUid = userList.stream()"
                        + ".collect(Collectors.toMap(User::getUid, User::getLastChannel));\n");

        assertTrue(processed.contains("List<User> userList = this.userService.listByIds(uidList);"));
    }

    @Test
    void restoresRawMapTypeFromCollectorsToMapIdentity() {
        String processed = new SourcePostProcessor().process(
                "Map configMap = this.coinConfigService.listByCoinNames(null).stream()"
                        + ".collect(Collectors.toMap(CoinConfig::getCoinName, Function.identity()));\n"
                        + "configMap.forEach((name, config) -> resp.setCoinName(name));\n");

        assertTrue(processed.contains("Map<String, CoinConfig> configMap = this.coinConfigService"));
    }

    @Test
    void restoresRawMapTypeFromCollectorsToMapValueMethodReference() {
        String processed = new SourcePostProcessor().process(
                "Map booleanMap = list.stream().collect(Collectors.toMap(CheckBalanceDTO::getUid, "
                        + "CheckBalanceDTO::getCheckRes));\n");

        assertTrue(processed.contains("Map<Long, Boolean> booleanMap = list.stream()"));
    }

    @Test
    void restoresRawMapTypeFromGetOrDefaultDefaultValue() {
        String processed = new SourcePostProcessor().process(
                "Map map = queryPointsUserBalancelResp.getMap();\n"
                        + "list.forEach(entity -> entity.setBalance(map.getOrDefault(entity.getUid(), "
                        + "new BigDecimal(\"0\"))));\n");

        assertTrue(processed.contains("Map<Long, BigDecimal> map = queryPointsUserBalancelResp.getMap();"));
    }

    @Test
    void restoresGetBeansOfTypeMapTypeFromClassLiteral() {
        String processed = new SourcePostProcessor().process(
                "Map beansOfType = ApplicationContextUtils.getBeansOfType(CloseTimeoutOrderService.class);\n"
                        + "beansOfType.forEach((beanName, bean) -> bean.executeScanAndClose(days));\n");

        assertTrue(processed.contains("Map<String, CloseTimeoutOrderService> beansOfType = "
                + "ApplicationContextUtils.getBeansOfType(CloseTimeoutOrderService.class);"));
    }

    @Test
    void restoresListTypeFromStreamMapGetterResult() {
        String processed = new SourcePostProcessor().process(
                "List<PointsConsumptionUserDailyStatsSummaryDto> list;\n"
                        + "List uidList = list.stream().map(PointsConsumptionUserDailyStatsSummaryDto::getUid)"
                        + ".collect(Collectors.toList());\n");

        assertTrue(processed.contains("List<Long> uidList = list.stream()"));
    }

    @Test
    void restoresPreviousListObjectTypeFromStreamAssignment() {
        String processed = new SourcePostProcessor().process(
                "List<Object> uidList = new ArrayList();\n"
                        + "if (StrUtil.isNotBlank(uids) && (uidList = Arrays.stream(uids.split(\",\"))"
                        + ".map(Long::parseLong).toList()).size() > 100) {}\n");

        assertTrue(processed.contains("List<Long> uidList = new ArrayList<>();"));
    }

    @Test
    void restoresDiamondConstructorsForTypedCollectionAssignments() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    private static Map<String, List<Field>> logFieldCache = new ConcurrentHashMap();\n"
                        + "    void load() {\n"
                        + "        List<Account> accountList = new ArrayList();\n"
                        + "        Set<Long> exclusivePacketUids = new HashSet();\n"
                        + "        Map<String, Object> typedMap = new HashMap(16);\n"
                        + "        HashMap rawMap = new HashMap(16);\n"
                        + "        Map<String, Object> anonymousMap = new HashMap() { };\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("Map<String, List<Field>> logFieldCache = new ConcurrentHashMap<>();"));
        assertTrue(processed.contains("List<Account> accountList = new ArrayList<>();"));
        assertTrue(processed.contains("Set<Long> exclusivePacketUids = new HashSet<>();"));
        assertTrue(processed.contains("Map<String, Object> typedMap = new HashMap<>(16);"));
        assertTrue(processed.contains("HashMap rawMap = new HashMap(16);"));
        assertTrue(processed.contains("Map<String, Object> anonymousMap = new HashMap() { };"));
    }

    @Test
    void unwrapsSFunctionArrayInLambdaWrapperSelect() {
        String processed = new SourcePostProcessor().process(
                "new LambdaQueryWrapper<SysChannelSourceConfig>().select(new SFunction[]{"
                        + "SysChannelSourceConfig::getChannelSourceId, SysChannelSourceConfig::getChannelName})"
                        + ".orderByAsc(SysChannelSourceConfig::getChannelSourceId);\n");

        assertTrue(processed.contains(".select(SysChannelSourceConfig::getChannelSourceId, "
                + "SysChannelSourceConfig::getChannelName)"));
        assertFalse(processed.contains("new SFunction[]"));
    }

    @Test
    void removesRawListCastFromGuavaPartitionSource() {
        String processed = new SourcePostProcessor().process(
                "List<User> userList;\n"
                        + "Lists.partition((List)userList, (int)100).stream()"
                        + ".map(elist -> elist.stream().map(User::getUid).collect(Collectors.toList()));\n");

        assertTrue(processed.contains("Lists.partition(userList, 100).stream()"));
        assertFalse(processed.contains("Lists.partition((List)userList"));
    }

    @Test
    void removesWrapperCastAroundTypedLambdaQueryWrapper() {
        String processed = new SourcePostProcessor().process(
                "this.sysChannelSourceConfigService.list((Wrapper)new LambdaQueryWrapper<SysChannelSourceConfig>()"
                        + ".orderByAsc(SysChannelSourceConfig::getChannelSourceId));\n");

        assertTrue(processed.contains("list(new LambdaQueryWrapper<SysChannelSourceConfig>()"));
        assertFalse(processed.contains("list((Wrapper)new LambdaQueryWrapper"));
    }

    @Test
    void removesWrapperCastAfterLambdaQueryWrapperTypeRestoration() {
        String processed = new SourcePostProcessor().process(
                "this.sysChannelSourceConfigService.list((Wrapper)new LambdaQueryWrapper()"
                        + ".orderByAsc(SysChannelSourceConfig::getChannelSourceId));\n");

        assertTrue(processed.contains("list(new LambdaQueryWrapper<SysChannelSourceConfig>()"));
        assertFalse(processed.contains("(Wrapper)new LambdaQueryWrapper"));
    }

    @Test
    void restoresFilteredStreamResultListTypeFromSourceList() {
        String processed = new SourcePostProcessor().process(
                "List<User> userList;\n"
                        + "List users = userList.stream().filter(e -> e.getUid() > 0)"
                        + ".collect(Collectors.toList());\n");

        assertTrue(processed.contains("List<User> users = userList.stream()"));
    }

    @Test
    void restoresStreamMapNewExpressionResultType() {
        String processed = new SourcePostProcessor().process(
                "List<BaseFeeDTO> assistList = adFeeUpdateReqList.stream().map(adFeeUpdateReq -> "
                        + "new BaseFee().setId(adFeeUpdateReq.getId())).collect(Collectors.toList());\n");

        assertTrue(processed.contains("List<BaseFee> assistList = adFeeUpdateReqList.stream()"));
    }

    @Test
    void restoresStreamMapNewExpressionResultTypeAfterFilter() {
        String processed = new SourcePostProcessor().process(
                "List assistList = userList.stream().filter(e -> ok(e)).map(user -> "
                        + "new UserAssist().setUid(user.getUid())).collect(Collectors.toList());\n");

        assertTrue(processed.contains("List<UserAssist> assistList = userList.stream()"));
    }

    @Test
    void restoresRawArrayListTypeFromUidStreamArgument() {
        String processed = new SourcePostProcessor().process(
                "ArrayList removeBalanceFail = new ArrayList();\n"
                        + "Lists.partition(userList, (int)100).stream().map(elist -> "
                        + "this.userService.checkBalance(elist.stream().map(User::getUid)"
                        + ".collect(Collectors.toList()), removeBalanceFail));\n");

        assertTrue(processed.contains("List<Long> removeBalanceFail = new ArrayList<>();"));
    }

    @Test
    void restoresRawArrayListTypeFromUidStreamMiddleArgument() {
        String processed = new SourcePostProcessor().process(
                "ArrayList removeBalanceFail = new ArrayList();\n"
                        + "this.userService.getZhOtcBalanceDTO(elist.stream().map(User::getUid)"
                        + ".collect(Collectors.toList()), removeBalanceFail, isCheckAll);\n");

        assertTrue(processed.contains("List<Long> removeBalanceFail = new ArrayList<>();"));
    }

    @Test
    void castsOptionalOrElseGetMapSupplierUsingMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "private Map<Long, String> getUserMap(User user, List<Long> uidList) {\n"
                        + "    return Optional.ofNullable(user).map(e -> (Map<Long, String>)"
                        + "ImmutableMap.<Long, String>builder().put(e.getUid(), e.getIdentify()).build())"
                        + ".orElseGet(() -> this.userService.listByIds((Collection)uidList).stream()"
                        + ".collect(Collectors.toMap(User::getUid, User::getIdentify)));\n"
                        + "}\n");

        assertTrue(processed.contains(".orElseGet(() -> (Map<Long, String>)this.userService.listByIds"));
    }

    @Test
    void restoresRawListTypeFromLaterElementCast() {
        String processed = new SourcePostProcessor().process(
                "List userList;\n"
                        + "startUid = ((User)userList.get(userList.size() - 1)).getUid();\n");

        assertTrue(processed.contains("List<User> userList;"));
    }

    @Test
    void restoresRawListTypeFromLaterStreamMethodReference() {
        String processed = new SourcePostProcessor().process(
                "List list;\n"
                        + "List uidList = list.stream().map(PointsConsumptionUserDailyStatsSummaryDto::getUid)"
                        + ".collect(Collectors.toList());\n");

        assertTrue(processed.contains("List<PointsConsumptionUserDailyStatsSummaryDto> list;"));
    }

    @Test
    void restoresStringObjectMapListTypesFromStringKeyUsage() {
        String processed = new SourcePostProcessor().process(
                "List<Map> list = this.accountService.listDuplicateReg();\n"
                        + "for (Map accountMap : list) {\n"
                        + "    accountMap.get(\"account\");\n"
                        + "}\n");

        assertTrue(processed.contains("List<Map<String, Object>> list = "
                + "this.accountService.listDuplicateReg();"));
        assertTrue(processed.contains("for (Map<String, Object> accountMap : list)"));
    }

    @Test
    void restoresPageDataReturnListLocalType() {
        String processed = new SourcePostProcessor().process(
                "public PageData<SendSmsLogsDTO> queryPage() {\n"
                        + "    List sendSmsLogsDTOS = SendSmsLogsMapstruct.INSTANCE.toList(apiPage.getRowList());\n"
                        + "    sendSmsLogsDTOS.forEach(item -> item.setIdentify(null));\n"
                        + "    return new PageData(sendSmsLogsDTOS, total);\n"
                        + "}\n");

        assertTrue(processed.contains("List<SendSmsLogsDTO> sendSmsLogsDTOS = "
                + "SendSmsLogsMapstruct.INSTANCE.toList(apiPage.getRowList());"));
    }

    @Test
    void restoresRawListElementTypeFromLaterEnhancedForInSameMethod() {
        String processed = new SourcePostProcessor().process(
                "private void checkRiskDuplicateConf(GroupMsgRiskConfDTO riskConfig) {\n"
                        + "    List riskConfigs = riskConfig.getRiskConfigs();\n"
                        + "    if (CollUtil.isNotEmpty((Collection)riskConfigs)) {\n"
                        + "        for (GroupRiskConfDTO config : riskConfigs) {}\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("List<GroupRiskConfDTO> riskConfigs = riskConfig.getRiskConfigs();"));
    }

    @Test
    void restoresRawListDeclarationTypeFromLaterEnhancedForAssignment() {
        String processed = new SourcePostProcessor().process(
                "private void checkRiskDuplicateConf(GroupMsgRiskConfDTO riskConfig) {\n"
                        + "    List numRiskConfigs;\n"
                        + "    if (CollUtil.isNotEmpty((Collection)(numRiskConfigs = riskConfig.getRiskLimitNumConfigs()))) {\n"
                        + "        for (GroupRiskConfDTO dto : numRiskConfigs) {}\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("List<GroupRiskConfDTO> numRiskConfigs;"));
    }

    @Test
    void restoresStringLocalFromToStringAssignment() {
        String processed = new SourcePostProcessor().process(
                "private String getRequestUrl(HttpServletRequest request) {\n"
                        + "    Object url = request.getRequestURL().toString();\n"
                        + "    return url;\n"
                        + "}\n");

        assertTrue(processed.contains("String url = request.getRequestURL().toString();"));
    }

    @Test
    void restoresIdentifyMapValueTypeFromKnownLookupMethod() {
        String processed = new SourcePostProcessor().process(
                "Map userMap = this.userService.getIdentifyMap(uids);\n"
                        + "respList.forEach(entity -> entity.setIdentify(userMap.getOrDefault(entity.getUid(), \"\")));\n");

        assertTrue(processed.contains("Map<Long, String> userMap = this.userService.getIdentifyMap(uids);"));
    }

    @Test
    void restoresMapValueTypeFromHashMapInitializer() {
        String processed = new SourcePostProcessor().process(
                "Map<Long, Object> groupNameMap = new HashMap<Long, Groups>();\n");

        assertTrue(processed.contains("Map<Long, Groups> groupNameMap = new HashMap<Long, Groups>();"));
    }

    @Test
    void restoresRawHashMapTypeFromTypedMethodArgument() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    private static void parse(Object record, Map<String, Object> colValMap, Field field) {\n"
                        + "    }\n"
                        + "    private static void parse(Object record, Object fieldValue, Map<String, Object> colValMap,"
                        + " Field field) {\n"
                        + "    }\n"
                        + "    private static void nested(Object value, Map<String, HashMap<String, Object>> valueMap) {\n"
                        + "    }\n"
                        + "    void run(Object record, Field field, Object item) {\n"
                        + "        HashMap oldValMap = new HashMap(16);\n"
                        + "        parse(record, oldValMap, (Field)field);\n"
                        + "        HashMap fieldMap = new HashMap(16);\n"
                        + "        parse(record, item, fieldMap, (Field)field);\n"
                        + "        HashMap fieldListValMap = new HashMap(16);\n"
                        + "        nested(item, fieldListValMap);\n"
                        + "        HashMap rawMap = new HashMap(16);\n"
                        + "        unknown(rawMap);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("HashMap<String, Object> oldValMap = new HashMap<>(16);"));
        assertTrue(processed.contains("HashMap<String, Object> fieldMap = new HashMap<>(16);"));
        assertTrue(processed.contains(
                "HashMap<String, HashMap<String, Object>> fieldListValMap = new HashMap<>(16);"));
        assertTrue(processed.contains("HashMap rawMap = new HashMap(16);"));
    }

    @Test
    void restoresRawHashMapTypeFromTypedPutValue() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run() {\n"
                        + "        HashMap valueMap = new HashMap(16);\n"
                        + "        HashMap<String, Object> rowMap = new HashMap<>(16);\n"
                        + "        String traceId = UUID.randomUUID().toString();\n"
                        + "        valueMap.put(traceId, rowMap);\n"
                        + "        HashMap literalKeyMap = new HashMap();\n"
                        + "        literalKeyMap.put(\"module\", rowMap);\n"
                        + "        HashMap rawMap = new HashMap(16);\n"
                        + "        Object rawValue = rowMap;\n"
                        + "        rawMap.put(traceId, rawValue);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "HashMap<String, HashMap<String, Object>> valueMap = new HashMap<>(16);"));
        assertTrue(processed.contains(
                "HashMap<String, HashMap<String, Object>> literalKeyMap = new HashMap<>();"));
        assertTrue(processed.contains("HashMap rawMap = new HashMap(16);"));
    }

    @Test
    void restoresMapEntryEnhancedForTypeFromKeyAndValueAssignments() {
        String processed = new SourcePostProcessor().process(
                "for (Map.Entry orderEntry : toBeRefundOrderMap.entrySet()) {\n"
                        + "    Long orderId = orderEntry.getKey();\n"
                        + "    ExchangeOrder exchangeOrder = (ExchangeOrder)orderEntry.getValue();\n"
                        + "}\n");

        assertTrue(processed.contains("for (Map.Entry<Long, ExchangeOrder> orderEntry : "
                + "toBeRefundOrderMap.entrySet())"));
    }

    @Test
    void widensShiroFilterMapValuesToServletFilter() {
        String processed = new SourcePostProcessor().process(
                "LinkedHashMap<String, JWTFilter> filters = new LinkedHashMap<String, JWTFilter>();\n"
                        + "filters.put(\"jwt\", new JWTFilter());\n"
                        + "shiroFilterFactoryBean.setFilters(filters);\n");

        assertTrue(processed.contains("LinkedHashMap<String, jakarta.servlet.Filter> filters = "
                + "new LinkedHashMap<String, jakarta.servlet.Filter>();"));
    }

    @Test
    void restoresGenericListMethodTypeVariableLocals() {
        String processed = new SourcePostProcessor().process(
                "public static <S, T> List<T> copyListProperties(List<S> sources, Supplier<T> target) {\n"
                        + "    ArrayList<Object> list = new ArrayList<Object>(sources.size());\n"
                        + "    for (S source : sources) {\n"
                        + "        Object t = BeanCopyUtils.copy(source, target);\n"
                        + "        list.add(t);\n"
                        + "    }\n"
                        + "    return list;\n"
                        + "}\n");

        assertTrue(processed.contains("ArrayList<T> list = new ArrayList<T>(sources.size());"));
        assertTrue(processed.contains("T t = BeanCopyUtils.copy(source, target);"));
    }

    @Test
    void scopesStreamMethodReferenceOwnerAlignmentToCurrentMethod() {
        String processed = new SourcePostProcessor().process(
                "public void first() {\n"
                        + "    List<RechargePointsPageDTO> respList = mapper.toRechargeList(resp.getRowList());\n"
                        + "    List uids = respList.stream().map(ConsumerPageDTO::getUid).collect(Collectors.toList());\n"
                        + "}\n"
                        + "public void second() {\n"
                        + "    List<ConsumerPageDTO> respList = mapper.toConsumerList(resp.getRowList());\n"
                        + "    List uids = respList.stream().map(RechargePointsPageDTO::getUid).collect(Collectors.toList());\n"
                        + "}\n");

        assertTrue(processed.contains("respList.stream().map(RechargePointsPageDTO::getUid)"));
        assertTrue(processed.contains("respList.stream().map(ConsumerPageDTO::getUid)"));
    }

    @Test
    void preservesStaticParseMethodReferencesWhenListElementTypeIsDifferent() {
        String processed = new SourcePostProcessor().process(
                "List<String> list = Arrays.stream(uids.split(\",\")).toList();\n"
                        + "List uidList = list.stream().map(Long::parseLong).collect(Collectors.toList());\n");

        assertTrue(processed.contains("list.stream().map(Long::parseLong)"));
        assertFalse(processed.contains("String::parseLong"));
    }

    @Test
    void addsListElementTypeWhenEnhancedForUsesTypedElement() {
        String processed = new SourcePostProcessor().process(
                "List findPetTypes = this.types.findPetTypes();\n"
                        + "        for (PetType type : findPetTypes) {\n"
                        + "        }\n");

        assertTrue(processed.contains("List<PetType> findPetTypes = this.types.findPetTypes();"));
        assertTrue(processed.contains("for (PetType type : findPetTypes)"));
    }

    @Test
    void addsOptionalElementTypeAndRemovesOrElseThrowCast() {
        String processed = new SourcePostProcessor().process(
                "Optional optionalOwner = this.owners.findById(Integer.valueOf(ownerId));\n"
                        + "        Owner owner = (Owner)optionalOwner.orElseThrow(() -> new IllegalArgumentException(\"missing\"));\n");

        assertTrue(processed.contains("Optional<Owner> optionalOwner = this.owners.findById(Integer.valueOf(ownerId));"));
        assertTrue(processed.contains("Owner owner = optionalOwner.orElseThrow"));
        assertFalse(processed.contains("(Owner)optionalOwner"));
    }

    @Test
    void replacesUnavailableAnonymousInnerClassPlaceholder() {
        String processed = new SourcePostProcessor().process(
                "private static final Runnable RUNNABLE = new /* Unavailable Anonymous Inner Class!! */;\n");

        assertTrue(processed.contains("RUNNABLE = null;"));
        assertFalse(processed.contains("Unavailable Anonymous Inner Class"));
    }

    @Test
    void replacesDuplicateAnonymousInnerClassPlaceholder() {
        String processed = new SourcePostProcessor().process(
                "worker.execute(new /* invalid duplicate definition of identical inner class */);\n");

        assertTrue(processed.contains("worker.execute(null);"));
        assertFalse(processed.contains("invalid duplicate definition"));
    }

    @Test
    void preservesSyntheticSwitchMapReferenceInsteadOfChangingCaseSemantics() {
        String processed = new SourcePostProcessor().process(
                "switch (1.$SwitchMap$com$example$Mode[value.getMode().ordinal()]) {\n"
                        + "    case 1: break;\n"
                        + "}\n");

        assertTrue(processed.contains("1.$SwitchMap$com$example$Mode[value.getMode().ordinal()]"));
        assertFalse(processed.contains("switch (value.getMode().ordinal())"));
    }

    @Test
    void replacesNumericAnonymousClassFragments() {
        String processed = new SourcePostProcessor().process(
                "    private static final Runnable R = new 1();\n"
                        + "    private static final Handle H = new LocalPoolHandle((Pool) null, (1) null);\n"
                        + "    void add() {\n"
                        + "        2 strategy = null;\n"
                        + "    }\n");

        assertTrue(processed.contains("R = null;"));
        assertTrue(processed.contains("new LocalPoolHandle((Pool) null, null)"));
        assertTrue(processed.contains("Object strategy = null;"));
        assertFalse(processed.contains("new 1("));
        assertFalse(processed.contains("(1) null"));
    }

    @Test
    void replacesCfrVoidTemporaryLocalDeclarations() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "  void run() {\n"
                        + "    void var15_20;\n"
                        + "    System.out.println(1);\n"
                        + "  }\n"
                        + "}\n",
                "Sample");

        assertFalse(processed.contains("void var15_20;"));
        assertTrue(processed.contains("Object var15_20 = null;"));
    }

    @Test
    void removesDecompilerDiagnosticComments() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "  /*\n"
                        + "   * WARNING - void declaration\n"
                        + "   * Enabled force condition propagation\n"
                        + "   * Lifted jumps to return sites\n"
                        + "   */\n"
                        + "  Object run() {\n"
                        + "    synchronized (this) {\n"
                        + "      // ** MonitorExit[var0] (shouldn't be in output)\n"
                        + "      return value();\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n",
                "Sample");

        assertFalse(processed.contains("WARNING - void declaration"));
        assertFalse(processed.contains("Enabled force condition propagation"));
        assertFalse(processed.contains("Lifted jumps to return sites"));
        assertFalse(processed.contains("MonitorExit"));
        assertTrue(processed.contains("return value();"));
    }

    @Test
    void shortensQualifiedInnerClassInstanceCreation() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Ansi ansi, Handler handler, Method method) {\n"
                        + "        Ansi.Text text = ansi.new Ansi.Text(0);\n"
                        + "        Handler.Binding binding = handler.new Handler.Binding(method);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("ansi.new Text(0);"));
        assertTrue(processed.contains("handler.new Binding(method);"));
        assertFalse(processed.contains("ansi.new Ansi.Text"));
        assertFalse(processed.contains("handler.new Handler.Binding"));
    }

    @Test
    void infersNumericGenericPlaceholderFromEnhancedForElementType() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run() {\n"
                        + "        ArrayList<1> bookKeeping = new ArrayList<1>();\n"
                        + "        bookKeeping.add(new Runnable() { public void run() {} });\n"
                        + "        for (Runnable runnable : bookKeeping) {\n"
                        + "            runnable.run();\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("ArrayList<Runnable> bookKeeping = new ArrayList<Runnable>();"));
        assertFalse(processed.contains("ArrayList<1>"));
    }

    @Test
    void addsObjectElementTypeToRawListCastDeclarations() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    <T> T call(Command cmd) {\n"
                        + "        List results = (List)cmd.parse();\n"
                        + "        return Sample.firstElement(results);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("List<Object> results = (List<Object>)cmd.parse();"));
        assertFalse(processed.contains("List results = (List)"));
    }

    @Test
    void preservesRawListCastDeclarationsWhenElementTypeIsUnknown() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Map<Object, ArrayList<String>> done, Object key) {\n"
                        + "        List aliases = (List)done.get(key);\n"
                        + "        aliases.add(\"name\");\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("List aliases = (List)done.get(key);"));
        assertFalse(processed.contains("List<Object> aliases"));
    }

    @Test
    void widensExecutionExceptionCauseLocalsToException() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(ExecutionException ex) {\n"
                        + "        ExecutionException cause = ex.getCause() instanceof Exception ? (Exception)ex.getCause() : ex;\n"
                        + "        handle(cause);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "Exception cause = ex.getCause() instanceof Exception ? (Exception)ex.getCause() : ex;"));
        assertFalse(processed.contains("ExecutionException cause ="));
    }

    @Test
    void replacesAnsiTextSyntheticOuterReference() {
        String processed = new SourcePostProcessor().process(
                "class Help {\n"
                        + "    static ColorScheme defaultColorScheme(Ansi ansi) { return null; }\n"
                        + "    enum Ansi {\n"
                        + "        ON;\n"
                        + "        public class Text {\n"
                        + "            public Text(int maxLength) {\n"
                        + "                this(maxLength, Help.defaultColorScheme(this$0));\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("Help.defaultColorScheme(Ansi.this)"));
        assertFalse(processed.contains("this$0"));
    }

    @Test
    void hoistsForLoopCounterWhenReferencedByLaterLoop() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Capacity capacity) {\n"
                        + "        int i;\n"
                        + "        for (int done = 1; done < capacity.min; ++done) {\n"
                        + "            use(done);\n"
                        + "        }\n"
                        + "        for (i = done; i < capacity.max; ++i) {\n"
                        + "            use(i);\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("int done;\n        for (done = 1; done < capacity.min; ++done)"));
        assertFalse(processed.contains("for (int done = 1;"));
    }

    @Test
    void removesUndefinedGenericArrayCastFromToArrayArgument() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    Text[][] run(ArrayList<Text[]> result) {\n"
                        + "        return (Text[][])result.toArray((T[])new Text[result.size()][]);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("return (Text[][])result.toArray(new Text[result.size()][]);"));
        assertFalse(processed.contains("(T[])new"));
    }

    @Test
    void castsClassWildcardForEnumGenericFactories() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    Object run(Class<?> type, Class<?>[] elementType, String value) {\n"
                        + "        EnumSet<?> enumSet = EnumSet.noneOf(elementType[0]);\n"
                        + "        if (value == null) return enumSet;\n"
                        + "        return Enum.valueOf(type, value);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("EnumSet.noneOf((Class) elementType[0])"));
        assertTrue(processed.contains("return (Collection<Object>) enumSet;"));
        assertTrue(processed.contains("Enum.valueOf((Class) type, value)"));
    }

    @Test
    void restoresGenericDefaultExceptionHandlerConstruction() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    public <R> R parseWithHandler(IParseResultHandler2<R> handler, String[] args) {\n"
                        + "        return this.parseWithHandlers(handler, new DefaultExceptionHandler(), args);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("new DefaultExceptionHandler<R>()"));
        assertFalse(processed.contains("new DefaultExceptionHandler(),"));
    }

    @Test
    void widensRegexTransformerConditionalsToInterfaceType() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Option option) {\n"
                        + "        RegexTransformer trans = option.commandSpec == null ? RegexTransformer.createDefault() : option.commandSpec.negatableOptionTransformer();\n"
                        + "        trans.makeSynopsis(option.shortestName(), option.commandSpec);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("INegatableOptionTransformer trans = option.commandSpec == null"));
        assertFalse(processed.contains("RegexTransformer trans ="));
    }

    @Test
    void restoresPositionalParamSpecListLocals() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Model.ArgSpec argSpec, List<Model.PositionalParamSpec> positionals) {\n"
                        + "        List<Object> missingList = Collections.emptyList();\n"
                        + "        if (argSpec.isPositional()) {\n"
                        + "            missingList = positionals.subList(0, positionals.size());\n"
                        + "        }\n"
                        + "        createMissingParameterMessage(argSpec, missingList);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("List<Model.PositionalParamSpec> missingList = Collections.emptyList();"));
        assertFalse(processed.contains("List<Object> missingList"));
    }

    @Test
    void restoresWildcardClassLocalsForReflectionTraversal() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(UserObject userObject) {\n"
                        + "        Class<Object> cls;\n"
                        + "        Class<Object> clazz = cls = userObject.getType();\n"
                        + "        Stack<Class> hierarchy = new Stack<Class>();\n"
                        + "        for (cls = userObject.getType(); cls != null; cls = cls.getSuperclass()) {\n"
                        + "            hierarchy.add(cls);\n"
                        + "        }\n"
                        + "        cls = (Class)hierarchy.pop();\n"
                        + "        Command cmd = cls.getAnnotation(Command.class);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("Class<?> cls;"));
        assertTrue(processed.contains("Class<?> clazz = cls = userObject.getType();"));
        assertTrue(processed.contains("Stack<Class<?>> hierarchy = new Stack<Class<?>>();"));
        assertTrue(processed.contains("cls = hierarchy.pop();"));
        assertFalse(processed.contains("Class<Object>"));
        assertFalse(processed.contains("(Class)hierarchy.pop()"));
    }

    @Test
    void castsWildcardClassArrayElementsAddedToRawClassLists() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(List<Class> result, Class<?>[] aux) {\n"
                        + "        result.add(aux[0]);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("result.add((Class) aux[0]);"));
        assertFalse(processed.contains("result.add(aux[0]);"));
    }

    @Test
    void restoresGenericReturnLocalTypeForReturnedObjectVariables() {
        String processed = new SourcePostProcessor().process(
                "class Sample<K, V> {\n"
                        + "    public V put(K key, V value) {\n"
                        + "        Object removedValue = this.targetMap.remove(key);\n"
                        + "        this.targetMap.put(key, value);\n"
                        + "        return removedValue;\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("V removedValue = this.targetMap.remove(key);"));
        assertFalse(processed.contains("Object removedValue"));
    }

    @Test
    void splitsMapPutKeyAssignmentBeforeUse() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Map<String, Integer> m, String sequence, int i, int degree) {\n"
                        + "        String gram;\n"
                        + "        m.put(gram, 1 + (m.containsKey(gram = sequence.substring(i, i + degree)) ? (Integer)m.get(gram) : 0));\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("String gram = sequence.substring(i, i + degree);\n"
                + "        m.put(gram, 1 + (m.containsKey(gram) ? (Integer)m.get(gram) : 0));"));
        assertFalse(processed.contains("String gram;\n"));
    }

    @Test
    void restoresNestedStringConditionalAssignments() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    String get(String key, CommandSpec commandSpec) {\n"
                        + "        String result;\n"
                        + "        String string = \"COMMAND-NAME\".equals(key) ? commandSpec.name() : (result = \"ROOT-COMMAND-NAME\".equals(key) ? commandSpec.root().name() : null);\n"
                        + "        return result;\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("String result = \"COMMAND-NAME\".equals(key) ? commandSpec.name() : (\"ROOT-COMMAND-NAME\".equals(key) ? commandSpec.root().name() : null);"));
        assertFalse(processed.contains("String string ="));
    }

    @Test
    void restoresNestedIntConditionalAssignments() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    int compareTo(Range other) {\n"
                        + "        int result;\n"
                        + "        int n = this.anchor() < other.anchor() ? -1 : (result = this.anchor() == other.anchor() ? 0 : 1);\n"
                        + "        if (result == 0) {\n"
                        + "            int n2 = this.max < other.max ? -1 : (result = this.max == other.max ? 0 : 1);\n"
                        + "        }\n"
                        + "        return result;\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("int result = this.anchor() < other.anchor() ? -1 : (this.anchor() == other.anchor() ? 0 : 1);"));
        assertTrue(processed.contains("result = this.max < other.max ? -1 : (this.max == other.max ? 0 : 1);"));
        assertFalse(processed.contains("int n ="));
        assertFalse(processed.contains("int n2 ="));
    }

    @Test
    void restoresNestedIntConditionalAssignmentsAfterGuardBlocks() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    int compareTo(Range other) {\n"
                        + "        int result;\n"
                        + "        if (same(other)) {\n"
                        + "            return 0;\n"
                        + "        }\n"
                        + "        int n = this.anchor() < other.anchor() ? -1 : (result = this.anchor() == other.anchor() ? 0 : 1);\n"
                        + "        return result;\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("result = this.anchor() < other.anchor() ? -1 : (this.anchor() == other.anchor() ? 0 : 1);"));
        assertFalse(processed.contains("int n ="));
    }

    @Test
    void removesWildcardBoundsFromLambdaParameterList() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(ExtendedAttributes<K, V> attributes) {\n"
                        + "        attributes.forEach((? super K extendedAttributeKey, ? super V value) -> {\n"
                        + "            use(extendedAttributeKey, value);\n"
                        + "        });\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("attributes.forEach((extendedAttributeKey, value) -> {"));
        assertFalse(processed.contains("? super"));
    }

    @Test
    void balancesNullArgumentStatementsAfterAnonymousPlaceholderReplacement() {
        String processed = new SourcePostProcessor().process(
                "seedGeneratorThread.setUncaughtExceptionHandler(null;\n"
                        + "Object ret = AccessController.doPrivileged((PrivilegedAction<Object>) null;\n");

        assertTrue(processed.contains("setUncaughtExceptionHandler(null);"));
        assertTrue(processed.contains("doPrivileged((PrivilegedAction<Object>) null);"));
    }

    @Test
    void castsDoPrivilegedMethodReferenceToPrivilegedAction() {
        String processed = new SourcePostProcessor().process(
                "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    ClassLoader get() {\n"
                        + "        return AccessController.doPrivileged(Sample::directGetContextClassLoader);\n"
                        + "    }\n"
                        + "    static ClassLoader directGetContextClassLoader() { return null; }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<ClassLoader>) Sample::directGetContextClassLoader)"));
    }

    @Test
    void castsDoPrivilegedLambdaToPrivilegedActionUsingEnclosingReturnType() {
        String processed = new SourcePostProcessor().process(
                "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    private static ClassLoader getContextClassLoaderInternal() throws Exception {\n"
                        + "        return AccessController.doPrivileged(() -> Sample.directGetContextClassLoader());\n"
                        + "    }\n"
                        + "    static ClassLoader directGetContextClassLoader() { return null; }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> Sample.directGetContextClassLoader())"));
    }

    @Test
    void castsDoPrivilegedInsideIfBlockUsingEnclosingMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "import java.nio.file.Files;\n"
                        + "import java.nio.file.Path;\n"
                        + "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    static Boolean isSymbolicLink(Path file) {\n"
                        + "        if (System.getSecurityManager() == null) {\n"
                        + "            return Files.isSymbolicLink(file);\n"
                        + "        }\n"
                        + "        return AccessController.doPrivileged(() -> Files.isSymbolicLink(file));\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> Files.isSymbolicLink(file))"));
        assertFalse(processed.contains("PrivilegedAction<if>"));
    }

    @Test
    void castsDoPrivilegedInsideGenericMethodUsingGenericReturnType() {
        String processed = new SourcePostProcessor().process(
                "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    private static <T> T doPrivileged(PrivilegedAction<T> action) {\n"
                        + "        return AccessController.doPrivileged(action);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<T>) action)"));
        assertFalse(processed.contains("PrivilegedAction<<T> T>"));
    }

    @Test
    void removesUnreachableBreakAfterInfiniteLoopThatAlreadyReturnsOrThrows() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    Object row() {\n"
                        + "        while (true) {\n"
                        + "            if (done()) {\n"
                        + "                return new Object();\n"
                        + "            }\n"
                        + "            throw new RuntimeException();\n"
                        + "        }\n"
                        + "        break;\n"
                        + "    }\n"
                        + "}\n");

        assertFalse(processed.contains("        break;\n"));
        assertTrue(processed.contains("while (true)"));
    }

    @Test
    void restoresSyntheticEnumSwitchExpressionFromSwitchMap() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    String describe(GroupMemberLimitMsgType type) {\n"
                        + "        return switch (1.$SwitchMap$com$ochat$group$enums$GroupMemberLimitMsgType[type.ordinal()]) {\n"
                        + "            default -> throw new MatchException(null, null);\n"
                        + "            case 1 -> \"expire\";\n"
                        + "            case 2 -> \"balance\";\n"
                        + "            case 3 -> \"renew\";\n"
                        + "        };\n"
                        + "    }\n"
                        + "}\n",
                "demo.Sample",
                Map.of("$SwitchMap$com$ochat$group$enums$GroupMemberLimitMsgType",
                        Map.of(1, "EXPIRATION_REMINDER", 2, "BALANCE_INSUFFICIENT", 3, "AUTO_RENEW_SUCCESS")));

        assertTrue(processed.contains("return switch (type)"));
        assertTrue(processed.contains("case EXPIRATION_REMINDER -> \"expire\";"));
        assertTrue(processed.contains("case BALANCE_INSUFFICIENT -> \"balance\";"));
        assertTrue(processed.contains("case AUTO_RENEW_SUCCESS -> \"renew\";"));
        assertFalse(processed.contains("1.$SwitchMap"));
        assertFalse(processed.contains("case 1 ->"));
        assertFalse(processed.contains("MatchException"), processed);
    }

    @Test
    void restoresSyntheticEnumSwitchStatementUsingOriginalCaseOrder() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void send(NoticeSendType type) {\n"
                        + "        switch (1.$SwitchMap$com$example$share$enums$NoticeSendType[type.ordinal()]) {\n"
                        + "            case 1:\n"
                        + "                sendAll();\n"
                        + "                break;\n"
                        + "            case 2:\n"
                        + "                sendSelected();\n"
                        + "                break;\n"
                        + "            case 5:\n"
                        + "                sendNonAuth();\n"
                        + "                break;\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n",
                "demo.Sample",
                Map.of("$SwitchMap$com$example$share$enums$NoticeSendType",
                        Map.of(1, "ALL", 2, "SELECTED_USER", 5, "NON_AUTH")));

        assertTrue(processed.contains("switch (type)"));
        assertTrue(processed.contains("case ALL:"));
        assertTrue(processed.contains("case SELECTED_USER:"));
        assertTrue(processed.contains("case NON_AUTH:"));
        assertFalse(processed.contains("case 1:"));
        assertFalse(processed.contains("case 2:"));
        assertFalse(processed.contains("case 5:"));
    }

    @Test
    void restoresPageInfoTypeFromMapstructMethodParameterContext() {
        String processed = new SourcePostProcessor().process(
                "package demo;\n\n"
                        + "import com.ochat.framework.common.pojo.vo.PageInfo;\n"
                        + "import demo.mapstruct.GroupGameMapstruct;\n\n"
                        + "class Sample {\n"
                        + "    void load() {\n"
                        + "        PageInfo resp = api.orderPage(req);\n"
                        + "        resp.getRowList().stream().map(row -> {\n"
                        + "            Dto dto = GroupGameMapstruct.INSTANCE.toDto(row);\n"
                        + "            return dto;\n"
                        + "        }).collect(Collectors.toList());\n"
                        + "    }\n"
                        + "}\n",
                "demo.Sample",
                Map.of(),
                Map.of("GroupGameMapstruct", Map.of("toDto", "com.ochat.group.game.pojo.resp.OrderResp")));

        assertTrue(processed.contains("import com.ochat.group.game.pojo.resp.OrderResp;"));
        assertTrue(processed.contains("PageInfo<OrderResp> resp = api.orderPage(req);"));
    }

    @Test
    void restoresPageInfoTypeFromCastMapstructInstanceCall() {
        String processed = new SourcePostProcessor().process(
                "package demo;\n\n"
                        + "import com.ochat.framework.common.pojo.vo.PageInfo;\n"
                        + "import demo.mapstruct.SendCodeMapstruct;\n\n"
                        + "class Sample {\n"
                        + "    void load() {\n"
                        + "        PageInfo apiPage = api.getPage(req);\n"
                        + "        apiPage.getRowList().stream().map(arg_0 -> "
                        + "((SendCodeMapstruct)SendCodeMapstruct.INSTANCE).toDto(arg_0))"
                        + ".collect(Collectors.toList());\n"
                        + "    }\n"
                        + "}\n",
                "demo.Sample",
                Map.of(),
                Map.of("SendCodeMapstruct", Map.of("toDto", "com.ochat.sms.pojo.resp.SendCodeResp")));

        assertTrue(processed.contains("import com.ochat.sms.pojo.resp.SendCodeResp;"));
        assertTrue(processed.contains("PageInfo<SendCodeResp> apiPage = api.getPage(req);"));
    }

    @Test
    void restoresPageInfoTypeFromLocalMapperMethodParameter() {
        String processed = new SourcePostProcessor().process(
                "package demo;\n\n"
                        + "import com.ochat.framework.common.pojo.vo.PageInfo;\n"
                        + "import com.ochat.user.pojo.resp.DeviceResp;\n\n"
                        + "class Sample {\n"
                        + "    void load() {\n"
                        + "        PageInfo pageInfo = api.queryPage(req);\n"
                        + "        pageInfo.getRowList().stream().map(arg_0 -> this.toDTO(arg_0)).toList();\n"
                        + "    }\n"
                        + "    private Dto toDTO(DeviceResp resp) { return new Dto(); }\n"
                        + "}\n");

        assertTrue(processed.contains("PageInfo<DeviceResp> pageInfo = api.queryPage(req);"));
    }

    @Test
    void restoresSimpleThisMethodReferenceLambdas() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void load(PageInfo<DeviceResp> pageInfo) {\n"
                        + "        pageInfo.getRowList().stream().map(arg_0 -> this.toDTO(arg_0)).toList();\n"
                        + "        pageInfo.getRowList().stream().filter(arg_0 -> this.getClearTimeBool(arg_0)).toList();\n"
                        + "        pageInfo.getRowList().stream().map(arg_1 -> this.toDTO((DeviceResp)arg_1)).toList();\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains(".map(this::toDTO).toList()"));
        assertTrue(processed.contains(".filter(this::getClearTimeBool).toList()"));
        assertTrue(processed.contains(".map(arg_1 -> this.toDTO((DeviceResp)arg_1)).toList()"));
    }

    @Test
    void restoresPairListTypeFromTimeSplitterAssignment() {
        String processed = new SourcePostProcessor().process(
                "List<Pair> timeSegments = TimeSplitter.splitTimeRange(startDateTime, endDateTime);\n"
                        + "for (Pair segment : timeSegments) {\n"
                        + "    LocalDateTime start = (LocalDateTime)segment.getKey();\n"
                        + "}\n");

        assertTrue(processed.contains(
                "List<Pair<LocalDateTime, LocalDateTime>> timeSegments = TimeSplitter.splitTimeRange"));
        assertTrue(processed.contains("for (Pair<LocalDateTime, LocalDateTime> segment : timeSegments)"));
    }

    @Test
    void restoresFieldDataScanListTypesAndImports() {
        String processed = new SourcePostProcessor().process(
                "package demo;\n\n"
                        + "import com.utils.FieldData;\n"
                        + "import java.util.ArrayList;\n"
                        + "import java.util.List;\n\n"
                        + "class Sample {\n"
                        + "    void scan() {\n"
                        + "        List<FieldData> fieldDataList = encryptReplaceComponent.scanList(row);\n"
                        + "        List collect = fieldDataList.stream().map(FieldData::getField)"
                        + ".collect(Collectors.toList());\n"
                        + "        ArrayList ignoreAnnotations = Lists.newArrayList(new Class[]{TableId.class});\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("import java.lang.annotation.Annotation;"));
        assertTrue(processed.contains("import java.lang.reflect.Field;"));
        assertTrue(processed.contains("List<FieldData<Object>> fieldDataList = encryptReplaceComponent.scanList(row);"));
        assertTrue(processed.contains("List<Field> collect = fieldDataList.stream()"));
        assertTrue(processed.contains("ArrayList<Class<? extends Annotation>> ignoreAnnotations = "
                + "Lists.newArrayList(TableId.class);"));
    }

    @Test
    void restoresClassAnnotationListFieldInitializerFromRawClassArray() {
        String processed = new SourcePostProcessor().process(
                "package demo;\n\n"
                        + "import java.lang.annotation.Annotation;\n"
                        + "import java.util.List;\n\n"
                        + "class Sample {\n"
                        + "    public static final List<Class<? extends Annotation>> CRYPT_ANNOTATIONS = "
                        + "Lists.newArrayList(new Class[]{TableId.class, RelyCrypt.class});\n"
                        + "}\n");

        assertTrue(processed.contains("import java.util.Arrays;"));
        assertTrue(processed.contains("List<Class<? extends Annotation>> CRYPT_ANNOTATIONS = "
                + "Arrays.asList(TableId.class, RelyCrypt.class);"));
        assertFalse(processed.contains("new Class[]{"));
    }

    @Test
    void castsReflectionFieldGetterResultToGenericFunctionType() {
        String processed = new SourcePostProcessor().process(
                "public static <T, K> Function<T, K> createFieldFunction(String fieldName, Class<T> clazz) {\n"
                        + "    return obj -> {\n"
                        + "        try {\n"
                        + "            return field.get(obj);\n"
                        + "        } catch (IllegalAccessException e) {\n"
                        + "            throw new RuntimeException(e);\n"
                        + "        }\n"
                        + "    };\n"
                        + "}\n");

        assertTrue(processed.contains("return (K)field.get(obj);"));
    }

    @Test
    void restoresRawListLongValueOfStreamConversion() {
        String processed = new SourcePostProcessor().process(
                "List<Long> ids = ((List)source).stream().map(Long::valueOf).collect(Collectors.toList());\n");

        assertTrue(processed.contains(
                "List<Long> ids = ((List<?>)source).stream().map(String::valueOf)"
                        + ".map(Long::valueOf).collect(Collectors.toList());"));
    }

    @Test
    void doesNotOverwriteMappedStreamResultTypeWithSourceListType() {
        String processed = new SourcePostProcessor().process(
                "List<Menu> menus = loadMenus();\n"
                        + "List<Long> ids = menus.stream().filter(e -> e.getMenuType().equals(\"3\"))"
                        + ".map(Menu::getMenuId).collect(Collectors.toList());\n");

        assertTrue(processed.contains("List<Long> ids = menus.stream()"));
        assertFalse(processed.contains("List<Menu> ids = menus.stream()"));
    }

    @Test
    void restoresBooleanHandleResultTernary() {
        String processed = new SourcePostProcessor().process(
                "public Result<Boolean> check() {\n"
                        + "    Role result = service.find();\n"
                        + "    return this.handleResult((result == null ? 1 : 0));\n"
                        + "}\n");

        assertTrue(processed.contains("return this.handleResult(result == null);"));
    }

    @Test
    void restoresPageDataGetListLocalTypeFromPageDataVariable() {
        String processed = new SourcePostProcessor().process(
                "PageData<GroupsMemberListDTO> pageData = service.query();\n"
                        + "List<GroupMember> groupsMemberListDTOList = pageData.getList();\n"
                        + "List<Long> uids = groupsMemberListDTOList.stream().map(GroupMember::getUid)"
                        + ".collect(Collectors.toList());\n");

        assertTrue(processed.contains("List<GroupsMemberListDTO> groupsMemberListDTOList = pageData.getList();"));
        assertTrue(processed.contains("groupsMemberListDTOList.stream().map(GroupsMemberListDTO::getUid)"));
    }

    @Test
    void restoresCheckedExceptionHandlingForReflectiveCountHelper() {
        String processed = new SourcePostProcessor().process(
                "public static long getCount(boolean methodAcceptsQueryRequest, Method method, "
                        + "Object instance, Object paramObj) {\n"
                        + "    Object result = null;\n"
                        + "    result = method.invoke(instance, paramObj);\n"
                        + "    return 0L;\n"
                        + "}\n");

        assertTrue(processed.contains("try {\n        Object result = null;"));
        assertTrue(processed.contains("try {\n        Object result = null;\n        result = method.invoke(instance, paramObj);"));
        assertTrue(processed.contains("catch (Exception e)"));
        assertTrue(processed.contains("ExceptionUtil.stacktraceToString(e)"));
        assertFalse(processed.contains("(Throwable)e"));
        assertTrue(processed.contains("throw new BusinessException(\"获取导出总数失败\");"));
    }

    @Test
    void restoresCheckedExceptionHandlingForClassForNameHelper() {
        String processed = new SourcePostProcessor().process(
                "private static Class<?> getClazz(String returnType) {\n"
                        + "    return Class.forName(returnType);\n"
                        + "}\n");

        assertTrue(processed.contains("try {"));
        assertTrue(processed.contains("return Class.forName(returnType);"));
        assertTrue(processed.contains("catch (ClassNotFoundException e)"));
    }

    @Test
    void catchesByteArrayOutputStreamTryWithResourceCloseFailure() {
        String processed = new SourcePostProcessor().process(
                "public static InputStream exportMultipleSheets() {\n"
                        + "    ByteArrayInputStream byteArrayInputStream;\n"
                        + "    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();){\n"
                        + "        byteArrayInputStream = new ByteArrayInputStream(outputStream.toByteArray());\n"
                        + "    }\n"
                        + "    return byteArrayInputStream;\n"
                        + "}\n");

        assertTrue(processed.contains("catch (Exception e) {\n        throw new RuntimeException(e);\n    }"));
    }

    @Test
    void restoresWorkbookCellLogicCheckedExceptionHandling() {
        String processed = new SourcePostProcessor().process(
                "public static <T> InputStream cellLogic(ByteArrayInputStream input, List<T> allData, Class<T> clazz) {\n"
                        + "    if (cell == null) {\n"
                        + "        return input;\n"
                        + "    }\n"
                        + "    XSSFWorkbook workbook = new XSSFWorkbook((InputStream)input);\n"
                        + "    workbook.write((OutputStream)out);\n"
                        + "    workbook.close();\n"
                        + "    return new ByteArrayInputStream(out.toByteArray());\n"
                        + "}\n");

        assertTrue(processed.contains("try {\n        XSSFWorkbook workbook = new XSSFWorkbook"));
        assertTrue(processed.contains("catch (Exception e) {\n        throw new RuntimeException(e);\n    }"));
    }
}
