/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.api.service;

import static org.apache.dolphinscheduler.api.AssertionsHelper.assertDoesNotThrow;
import static org.apache.dolphinscheduler.api.AssertionsHelper.assertThrowsServiceException;
import static org.apache.dolphinscheduler.api.constants.ApiFuncIdentificationConstant.DATASOURCE;
import static org.mockito.Mockito.when;

import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.permission.ResourcePermissionCheckService;
import org.apache.dolphinscheduler.api.service.impl.BaseServiceImpl;
import org.apache.dolphinscheduler.api.service.impl.DataSourceServiceImpl;
import org.apache.dolphinscheduler.api.utils.PageInfo;
import org.apache.dolphinscheduler.common.constants.DataSourceConstants;
import org.apache.dolphinscheduler.common.enums.AuthorizationType;
import org.apache.dolphinscheduler.common.enums.UserType;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.common.utils.PropertyUtils;
import org.apache.dolphinscheduler.dao.entity.DataSource;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.mapper.DataSourceMapper;
import org.apache.dolphinscheduler.dao.mapper.DataSourceUserMapper;
import org.apache.dolphinscheduler.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import org.apache.dolphinscheduler.plugin.datasource.api.datasource.DataSourceProcessor;
import org.apache.dolphinscheduler.plugin.datasource.api.plugin.DataSourceClientProvider;
import org.apache.dolphinscheduler.plugin.datasource.api.utils.CommonUtils;
import org.apache.dolphinscheduler.plugin.datasource.api.utils.DataSourceUtils;
import org.apache.dolphinscheduler.plugin.datasource.hive.param.HiveDataSourceParamDTO;
import org.apache.dolphinscheduler.plugin.datasource.mysql.param.MySQLConnectionParam;
import org.apache.dolphinscheduler.plugin.datasource.mysql.param.MySQLDataSourceParamDTO;
import org.apache.dolphinscheduler.plugin.datasource.oracle.param.OracleDataSourceParamDTO;
import org.apache.dolphinscheduler.plugin.datasource.postgresql.param.PostgreSQLDataSourceParamDTO;
import org.apache.dolphinscheduler.spi.datasource.ConnectionParam;
import org.apache.dolphinscheduler.spi.enums.DbConnectType;
import org.apache.dolphinscheduler.spi.enums.DbType;

import org.apache.commons.collections4.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * data source service test
 */
@ExtendWith(MockitoExtension.class)
public class DataSourceServiceTest {

    private static final Logger baseServiceLogger = LoggerFactory.getLogger(BaseServiceImpl.class);
    private static final Logger logger = LoggerFactory.getLogger(DataSourceServiceTest.class);
    private static final Logger dataSourceServiceLogger = LoggerFactory.getLogger(DataSourceServiceImpl.class);

    @InjectMocks
    private DataSourceServiceImpl dataSourceService;

    @Mock
    private DataSourceMapper dataSourceMapper;

    @Mock
    private DataSourceUserMapper datasourceUserMapper;

    @Mock
    private ResourcePermissionCheckService resourcePermissionCheckService;

    @Mock
    private IPage<DataSource> dataSourceList;

    private String randomStringWithLengthN(int n) {
        byte[] bitArray = new byte[n];
        new Random().nextBytes(bitArray);
        return new String(bitArray, StandardCharsets.UTF_8);
    }

    private void passResourcePermissionCheckService() {
        when(resourcePermissionCheckService.operationPermissionCheck(Mockito.any(), Mockito.anyInt(),
                Mockito.anyString(), Mockito.any())).thenReturn(true);
        when(resourcePermissionCheckService.resourcePermissionCheck(Mockito.any(), Mockito.any(),
                Mockito.anyInt(), Mockito.any())).thenReturn(true);
    }

    @Test
    public void createDataSourceTest() throws ExecutionException {
        User loginUser = getAdminUser();
        String dataSourceName = "dataSource01";
        String dataSourceDesc = "test dataSource";

        PostgreSQLDataSourceParamDTO postgreSqlDatasourceParam = new PostgreSQLDataSourceParamDTO();
        postgreSqlDatasourceParam.setDatabase(dataSourceName);
        postgreSqlDatasourceParam.setNote(dataSourceDesc);
        postgreSqlDatasourceParam.setHost("172.16.133.200");
        postgreSqlDatasourceParam.setPort(5432);
        postgreSqlDatasourceParam.setDatabase("dolphinscheduler");
        postgreSqlDatasourceParam.setUserName("postgres");
        postgreSqlDatasourceParam.setPassword("");
        postgreSqlDatasourceParam.setName(dataSourceName);

        // USER_NO_OPERATION_PERM
        assertThrowsServiceException(Status.USER_NO_OPERATION_PERM,
                () -> dataSourceService.createDataSource(loginUser, postgreSqlDatasourceParam));

        // DATASOURCE_EXIST
        List<DataSource> dataSourceList = new ArrayList<>();
        DataSource dataSource = new DataSource();
        dataSource.setName(dataSourceName);
        dataSourceList.add(dataSource);
        when(dataSourceMapper.queryDataSourceByName(dataSourceName.trim())).thenReturn(dataSourceList);
        passResourcePermissionCheckService();

        // DATASOURCE_EXIST
        assertThrowsServiceException(Status.DATASOURCE_EXIST,
                () -> dataSourceService.createDataSource(loginUser, postgreSqlDatasourceParam));

        try (
                MockedStatic<DataSourceClientProvider> mockedStaticDataSourceClientProvider =
                        Mockito.mockStatic(DataSourceClientProvider.class)) {

            when(dataSourceMapper.queryDataSourceByName(dataSourceName.trim())).thenReturn(null);

            // DESCRIPTION TOO LONG
            postgreSqlDatasourceParam.setNote(randomStringWithLengthN(512));
            assertThrowsServiceException(Status.DESCRIPTION_TOO_LONG_ERROR,
                    () -> dataSourceService.createDataSource(loginUser, postgreSqlDatasourceParam));
            postgreSqlDatasourceParam.setNote(dataSourceDesc);

            // SUCCESS
            assertDoesNotThrow(() -> dataSourceService.createDataSource(loginUser, postgreSqlDatasourceParam));

            // Duplicated Key Exception
            when(dataSourceMapper.insert(Mockito.any(DataSource.class))).thenThrow(DuplicateKeyException.class);
            assertThrowsServiceException(Status.DATASOURCE_EXIST,
                    () -> dataSourceService.createDataSource(loginUser, postgreSqlDatasourceParam));
        }
    }

    @Test
    public void updateDataSourceTest() {
        User loginUser = getAdminUser();

        int dataSourceId = 12;
        String dataSourceName = "dataSource01";
        String dataSourceDesc = "test dataSource";
        String dataSourceUpdateName = "dataSource01-update";

        PostgreSQLDataSourceParamDTO postgreSqlDatasourceParam = new PostgreSQLDataSourceParamDTO();
        postgreSqlDatasourceParam.setId(dataSourceId);
        postgreSqlDatasourceParam.setDatabase(dataSourceName);
        postgreSqlDatasourceParam.setNote(dataSourceDesc);
        postgreSqlDatasourceParam.setHost("172.16.133.200");
        postgreSqlDatasourceParam.setPort(5432);
        postgreSqlDatasourceParam.setDatabase("dolphinscheduler");
        postgreSqlDatasourceParam.setUserName("postgres");
        postgreSqlDatasourceParam.setPassword("");
        postgreSqlDatasourceParam.setName(dataSourceUpdateName);

        // RESOURCE_NOT_EXIST
        when(dataSourceMapper.selectById(dataSourceId)).thenReturn(null);
        assertThrowsServiceException(Status.RESOURCE_NOT_EXIST,
                () -> dataSourceService.updateDataSource(loginUser, postgreSqlDatasourceParam));

        // USER_NO_OPERATION_PERM
        DataSource dataSource = new DataSource();
        dataSource.setUserId(0);
        when(dataSourceMapper.selectById(dataSourceId)).thenReturn(dataSource);
        assertThrowsServiceException(Status.USER_NO_OPERATION_PERM,
                () -> dataSourceService.updateDataSource(loginUser, postgreSqlDatasourceParam));

        // DATASOURCE_EXIST
        dataSource.setName(dataSourceName);
        dataSource.setType(DbType.POSTGRESQL);
        dataSource.setConnectionParams(
                JSONUtils.toJsonString(DataSourceUtils.buildConnectionParams(postgreSqlDatasourceParam)));

        DataSource anotherDataSource = new DataSource();
        anotherDataSource.setName(dataSourceUpdateName);
        List<DataSource> dataSourceList = new ArrayList<>();
        dataSourceList.add(anotherDataSource);
        when(dataSourceMapper.selectById(dataSourceId)).thenReturn(dataSource);
        when(dataSourceMapper.queryDataSourceByName(postgreSqlDatasourceParam.getName()))
                .thenReturn(dataSourceList);
        passResourcePermissionCheckService();
        assertThrowsServiceException(Status.DATASOURCE_EXIST,
                () -> dataSourceService.updateDataSource(loginUser, postgreSqlDatasourceParam));

        try (
                MockedStatic<DataSourceClientProvider> mockedStaticDataSourceClientProvider =
                        Mockito.mockStatic(DataSourceClientProvider.class)) {
            // DATASOURCE_CONNECT_FAILED
            when(dataSourceMapper.queryDataSourceByName(postgreSqlDatasourceParam.getName())).thenReturn(null);

            // DESCRIPTION TOO LONG
            postgreSqlDatasourceParam.setNote(randomStringWithLengthN(512));
            assertThrowsServiceException(Status.DESCRIPTION_TOO_LONG_ERROR,
                    () -> dataSourceService.updateDataSource(loginUser, postgreSqlDatasourceParam));
            postgreSqlDatasourceParam.setNote(dataSourceDesc);

            // SUCCESS
            assertDoesNotThrow(() -> dataSourceService.updateDataSource(loginUser, postgreSqlDatasourceParam));

            // Duplicated Key Exception
            when(dataSourceMapper.updateById(Mockito.any(DataSource.class))).thenThrow(DuplicateKeyException.class);
            assertThrowsServiceException(Status.DATASOURCE_EXIST,
                    () -> dataSourceService.updateDataSource(loginUser, postgreSqlDatasourceParam));
        }
    }

    @Test
    public void testQueryDataSourceListPaging() {

        User adminUser = getAdminUser();
        User generalUser = getGeneralUser();
        String searchVal = "";
        int pageNo = 1;
        int pageSize = 10;

        PageInfo<DataSource> pageInfo =
                dataSourceService.queryDataSourceListPaging(adminUser, searchVal, pageNo, pageSize);
        Assertions.assertNotNull(pageInfo);

        // test query datasource as general user with no datasource authed
        when(dataSourceList.getRecords()).thenReturn(getSingleDataSourceList());
        when(dataSourceMapper.selectPagingByIds(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(dataSourceList);
        assertDoesNotThrow(() -> dataSourceService.queryDataSourceListPaging(generalUser, searchVal, pageNo, pageSize));

        // test query datasource as general user with datasource authed
        when(resourcePermissionCheckService.userOwnedResourceIdsAcquisition(AuthorizationType.DATASOURCE,
                generalUser.getId(), dataSourceServiceLogger)).thenReturn(Collections.singleton(1));

        assertDoesNotThrow(() -> dataSourceService.queryDataSourceListPaging(generalUser, searchVal, pageNo, pageSize));
    }

    @Test
    public void testConnectionTest() {
        int dataSourceId = -1;
        when(dataSourceMapper.selectById(dataSourceId)).thenReturn(null);
        assertThrowsServiceException(Status.RESOURCE_NOT_EXIST, () -> dataSourceService.connectionTest(dataSourceId));

        try (
                MockedStatic<DataSourceUtils> ignored =
                        Mockito.mockStatic(DataSourceUtils.class)) {
            DataSource dataSource = getOracleDataSource(999);
            when(dataSourceMapper.selectById(dataSource.getId())).thenReturn(dataSource);
            DataSourceProcessor dataSourceProcessor = Mockito.mock(DataSourceProcessor.class);

            when(DataSourceUtils.getDatasourceProcessor(Mockito.any())).thenReturn(dataSourceProcessor);
            when(dataSourceProcessor.checkDataSourceConnectivity(Mockito.any())).thenReturn(true);
            assertDoesNotThrow(() -> dataSourceService.connectionTest(dataSource.getId()));

            when(dataSourceProcessor.checkDataSourceConnectivity(Mockito.any())).thenReturn(false);
            assertThrowsServiceException(Status.CONNECTION_TEST_FAILURE,
                    () -> dataSourceService.connectionTest(dataSource.getId()));
        }

    }

    @Test
    public void testDelete() {
        User loginUser = getAdminUser();
        int dataSourceId = 1;
        // resource not exist
        when(dataSourceMapper.selectById(dataSourceId)).thenReturn(null);
        assertThrowsServiceException(Status.RESOURCE_NOT_EXIST,
                () -> dataSourceService.delete(loginUser, dataSourceId));

        // user no operation perm
        DataSource dataSource = new DataSource();
        dataSource.setUserId(0);
        when(dataSourceMapper.selectById(dataSourceId)).thenReturn(dataSource);
        assertThrowsServiceException(Status.USER_NO_OPERATION_PERM,
                () -> dataSourceService.delete(loginUser, dataSourceId));

        // success
        dataSource.setUserId(-1);
        loginUser.setUserType(UserType.ADMIN_USER);
        loginUser.setId(1);
        dataSource.setId(22);
        passResourcePermissionCheckService();
        when(dataSourceMapper.selectById(dataSourceId)).thenReturn(dataSource);
        assertDoesNotThrow(() -> dataSourceService.delete(loginUser, dataSourceId));

    }

    @Test
    public void testUnAuthDatasource() {
        User loginUser = getAdminUser();
        loginUser.setId(1);
        loginUser.setUserType(UserType.ADMIN_USER);
        int userId = 3;
        when(resourcePermissionCheckService.operationPermissionCheck(AuthorizationType.DATASOURCE,
                loginUser.getId(), null, baseServiceLogger)).thenReturn(true);
        when(resourcePermissionCheckService.resourcePermissionCheck(AuthorizationType.DATASOURCE, null, 0,
                baseServiceLogger)).thenReturn(true);
        // test admin user
        when(dataSourceMapper.queryAuthedDatasource(userId)).thenReturn(getSingleDataSourceList());
        when(dataSourceMapper.queryDatasourceExceptUserId(userId)).thenReturn(getDataSourceList());
        List<DataSource> dataSources = dataSourceService.unAuthDatasource(loginUser, userId);
        logger.info(dataSources.toString());
        Assertions.assertTrue(CollectionUtils.isNotEmpty(dataSources));

        // test non-admin user
        loginUser.setId(2);
        loginUser.setUserType(UserType.GENERAL_USER);
        when(dataSourceMapper.selectByMap(Collections.singletonMap("user_id", loginUser.getId())))
                .thenReturn(getDataSourceList());
        dataSources = dataSourceService.unAuthDatasource(loginUser, userId);
        logger.info(dataSources.toString());
        Assertions.assertTrue(CollectionUtils.isNotEmpty(dataSources));
    }

    @Test
    public void testAuthedDatasource() {
        User loginUser = getAdminUser();
        loginUser.setId(1);
        loginUser.setUserType(UserType.ADMIN_USER);
        int userId = 3;

        // test admin user
        when(dataSourceMapper.queryAuthedDatasource(userId)).thenReturn(getSingleDataSourceList());
        List<DataSource> dataSources = dataSourceService.authedDatasource(loginUser, userId);
        logger.info(dataSources.toString());
        Assertions.assertTrue(CollectionUtils.isNotEmpty(dataSources));

        // test non-admin user
        loginUser.setId(2);
        loginUser.setUserType(UserType.GENERAL_USER);
        dataSources = dataSourceService.authedDatasource(loginUser, userId);
        logger.info(dataSources.toString());
        Assertions.assertNotNull(dataSources);
    }

    @Test
    public void testQueryDataSourceList() {
        User adminUser = getAdminUser();
        assertDoesNotThrow(() -> dataSourceService.queryDataSourceList(adminUser, DbType.MYSQL.ordinal()));

        User generalUser = getGeneralUser();

        when(resourcePermissionCheckService.userOwnedResourceIdsAcquisition(AuthorizationType.DATASOURCE,
                generalUser.getId(), dataSourceServiceLogger)).thenReturn(Collections.emptySet());
        List<DataSource> emptyList = dataSourceService.queryDataSourceList(generalUser, DbType.MYSQL.ordinal());
        Assertions.assertEquals(emptyList.size(), 0);

        when(resourcePermissionCheckService.userOwnedResourceIdsAcquisition(AuthorizationType.DATASOURCE,
                generalUser.getId(), dataSourceServiceLogger)).thenReturn(Collections.singleton(1));

        DataSource dataSource = new DataSource();
        dataSource.setId(1);
        dataSource.setType(DbType.MYSQL);
        when(dataSourceMapper.selectBatchIds(Collections.singleton(1)))
                .thenReturn(Collections.singletonList(dataSource));

        List<DataSource> list =
                dataSourceService.queryDataSourceList(generalUser, DbType.MYSQL.ordinal());
        Assertions.assertNotNull(list);
    }

    @Test
    public void verifyDataSourceNameTest() {
        User loginUser = new User();
        loginUser.setUserType(UserType.GENERAL_USER);
        String dataSourceName = "dataSource1";
        when(dataSourceMapper.queryDataSourceByName(dataSourceName)).thenReturn(getDataSourceList());
        assertThrowsServiceException(Status.DATASOURCE_EXIST,
                () -> dataSourceService.verifyDataSourceName(dataSourceName));
    }

    @Test
    public void testQueryDataSource() {
        // datasource not exists
        when(dataSourceMapper.selectById(999)).thenReturn(null);
        User loginUser = new User();
        loginUser.setUserType(UserType.GENERAL_USER);
        loginUser.setId(2);

        assertThrowsServiceException(Status.RESOURCE_NOT_EXIST,
                () -> dataSourceService.queryDataSource(999, loginUser));

        DataSource dataSource = getOracleDataSource(1);
        when(dataSourceMapper.selectById(dataSource.getId())).thenReturn(dataSource);
        when(resourcePermissionCheckService.operationPermissionCheck(AuthorizationType.DATASOURCE,
                loginUser.getId(), DATASOURCE, baseServiceLogger)).thenReturn(true);

        // no perm
        when(resourcePermissionCheckService.resourcePermissionCheck(AuthorizationType.DATASOURCE,
                new Object[]{dataSource.getId()}, loginUser.getId(), baseServiceLogger)).thenReturn(false);
        assertThrowsServiceException(Status.USER_NO_OPERATION_PERM,
                () -> dataSourceService.queryDataSource(dataSource.getId(), loginUser));

        // success
        when(resourcePermissionCheckService.resourcePermissionCheck(AuthorizationType.DATASOURCE,
                new Object[]{dataSource.getId()}, loginUser.getId(), baseServiceLogger)).thenReturn(true);
        BaseDataSourceParamDTO paramDTO = dataSourceService.queryDataSource(dataSource.getId(), loginUser);
        Assertions.assertNotNull(paramDTO);
    }

    private List<DataSource> getDataSourceList() {

        List<DataSource> dataSources = new ArrayList<>();
        dataSources.add(getOracleDataSource(1));
        dataSources.add(getOracleDataSource(2));
        dataSources.add(getOracleDataSource(3));
        return dataSources;
    }

    private List<DataSource> getSingleDataSourceList() {
        return Collections.singletonList(getOracleDataSource(3));
    }

    private DataSource getOracleDataSource() {
        DataSource dataSource = new DataSource();
        dataSource.setName("test");
        dataSource.setNote("Note");
        dataSource.setType(DbType.ORACLE);
        dataSource.setConnectionParams(
                "{\"connectType\":\"ORACLE_SID\",\"address\":\"jdbc:oracle:thin:@192.168.xx.xx:49161\",\"database\":\"XE\","
                        + "\"jdbcUrl\":\"jdbc:oracle:thin:@192.168.xx.xx:49161/XE\",\"user\":\"system\",\"password\":\"oracle\"}");

        return dataSource;
    }

    private DataSource getOracleDataSource(int dataSourceId) {
        DataSource dataSource = new DataSource();
        dataSource.setId(dataSourceId);
        dataSource.setName("test");
        dataSource.setNote("Note");
        dataSource.setType(DbType.ORACLE);
        dataSource.setConnectionParams(
                "{\"connectType\":\"ORACLE_SID\",\"address\":\"jdbc:oracle:thin:@192.168.xx.xx:49161\",\"database\":\"XE\","
                        + "\"jdbcUrl\":\"jdbc:oracle:thin:@192.168.xx.xx:49161/XE\",\"user\":\"system\",\"password\":\"oracle\"}");

        return dataSource;
    }

    @Test
    public void buildParameter() {
        OracleDataSourceParamDTO oracleDatasourceParamDTO = new OracleDataSourceParamDTO();
        oracleDatasourceParamDTO.setHost("192.168.9.1");
        oracleDatasourceParamDTO.setPort(1521);
        oracleDatasourceParamDTO.setDatabase("im");
        oracleDatasourceParamDTO.setUserName("test");
        oracleDatasourceParamDTO.setPassword("test");
        oracleDatasourceParamDTO.setConnectType(DbConnectType.ORACLE_SERVICE_NAME);

        ConnectionParam connectionParam = DataSourceUtils.buildConnectionParams(oracleDatasourceParamDTO);
        String expected =
                "{\"user\":\"test\",\"password\":\"test\",\"address\":\"jdbc:oracle:thin:@//192.168.9.1:1521\",\"database\":\"im\",\"jdbcUrl\":\"jdbc:oracle:thin:@//192.168.9.1:1521/im\","
                        + "\"driverClassName\":\"oracle.jdbc.OracleDriver\",\"validationQuery\":\"select 1 from dual\",\"connectType\":\"ORACLE_SERVICE_NAME\"}";
        Assertions.assertEquals(expected, JSONUtils.toJsonString(connectionParam));

        try (MockedStatic<CommonUtils> mockedStaticCommonUtils = Mockito.mockStatic(CommonUtils.class)) {
            mockedStaticCommonUtils.when(CommonUtils::getKerberosStartupState).thenReturn(true);
            HiveDataSourceParamDTO hiveDataSourceParamDTO = new HiveDataSourceParamDTO();
            hiveDataSourceParamDTO.setHost("192.168.9.1");
            hiveDataSourceParamDTO.setPort(10000);
            hiveDataSourceParamDTO.setDatabase("im");
            hiveDataSourceParamDTO.setPrincipal("hive/hdfs-mycluster@ESZ.COM");
            hiveDataSourceParamDTO.setUserName("test");
            hiveDataSourceParamDTO.setPassword("test");
            hiveDataSourceParamDTO.setJavaSecurityKrb5Conf("/opt/krb5.conf");
            hiveDataSourceParamDTO.setLoginUserKeytabPath("/opt/hdfs.headless.keytab");
            hiveDataSourceParamDTO.setLoginUserKeytabUsername("test2/hdfs-mycluster@ESZ.COM");
            connectionParam = DataSourceUtils.buildConnectionParams(hiveDataSourceParamDTO);

            expected =
                    "{\"user\":\"test\",\"password\":\"test\",\"address\":\"jdbc:hive2://192.168.9.1:10000\",\"database\":\"im\","
                            + "\"jdbcUrl\":\"jdbc:hive2://192.168.9.1:10000/im\",\"driverClassName\":\"org.apache.hive.jdbc.HiveDriver\",\"validationQuery\":\"select 1\","
                            + "\"principal\":\"hive/hdfs-mycluster@ESZ.COM\",\"javaSecurityKrb5Conf\":\"/opt/krb5.conf\",\"loginUserKeytabUsername\":\"test2/hdfs-mycluster@ESZ.COM\","
                            + "\"loginUserKeytabPath\":\"/opt/hdfs.headless.keytab\"}";
            Assertions.assertEquals(expected, JSONUtils.toJsonString(connectionParam));
        }
    }

    @Test
    public void buildParameterWithDecodePassword() {
        try (MockedStatic<PropertyUtils> mockedStaticPropertyUtils = Mockito.mockStatic(PropertyUtils.class)) {
            mockedStaticPropertyUtils
                    .when(() -> PropertyUtils.getBoolean(DataSourceConstants.DATASOURCE_ENCRYPTION_ENABLE, false))
                    .thenReturn(true);
            Map<String, String> other = new HashMap<>();
            other.put("autoDeserialize", "yes");
            other.put("allowUrlInLocalInfile", "true");
            other.put("useSSL", "true");
            MySQLDataSourceParamDTO mysqlDatasourceParamDTO = new MySQLDataSourceParamDTO();
            mysqlDatasourceParamDTO.setHost("192.168.9.1");
            mysqlDatasourceParamDTO.setPort(1521);
            mysqlDatasourceParamDTO.setDatabase("im");
            mysqlDatasourceParamDTO.setUserName("test");
            mysqlDatasourceParamDTO.setPassword("123456");
            mysqlDatasourceParamDTO.setOther(other);
            ConnectionParam connectionParam = DataSourceUtils.buildConnectionParams(mysqlDatasourceParamDTO);
            String expected =
                    "{\"user\":\"test\",\"password\":\"bnVsbE1USXpORFUy\",\"address\":\"jdbc:mysql://192.168.9.1:1521\","
                            +
                            "\"database\":\"im\",\"jdbcUrl\":\"jdbc:mysql://192.168.9.1:1521/im\",\"driverClassName\":\"com.mysql.cj.jdbc.Driver\","
                            +
                            "\"validationQuery\":\"select 1\",\"other\":{\"autoDeserialize\":\"yes\",\"allowUrlInLocalInfile\":\"true\",\"useSSL\":\"true\"}}";
            Assertions.assertEquals(expected, JSONUtils.toJsonString(connectionParam));
        }

        MySQLDataSourceParamDTO mysqlDatasourceParamDTO = new MySQLDataSourceParamDTO();
        mysqlDatasourceParamDTO.setHost("192.168.9.1");
        mysqlDatasourceParamDTO.setPort(1521);
        mysqlDatasourceParamDTO.setDatabase("im");
        mysqlDatasourceParamDTO.setUserName("test");
        mysqlDatasourceParamDTO.setPassword("123456");
        ConnectionParam connectionParam = DataSourceUtils.buildConnectionParams(mysqlDatasourceParamDTO);
        String expected =
                "{\"user\":\"test\",\"password\":\"123456\",\"address\":\"jdbc:mysql://192.168.9.1:1521\",\"database\":\"im\","
                        + "\"jdbcUrl\":\"jdbc:mysql://192.168.9.1:1521/im\",\"driverClassName\":\"com.mysql.cj.jdbc.Driver\",\"validationQuery\":\"select 1\"}";
        Assertions.assertEquals(expected, JSONUtils.toJsonString(connectionParam));
    }

    /**
     * get Mock Admin User
     *
     * @return admin user
     */
    private User getAdminUser() {
        User loginUser = new User();
        loginUser.setId(1);
        loginUser.setUserName("admin");
        loginUser.setUserType(UserType.ADMIN_USER);
        return loginUser;
    }

    private User getGeneralUser() {
        User loginUser = new User();
        loginUser.setId(2);
        loginUser.setUserName("user");
        loginUser.setUserType(UserType.GENERAL_USER);
        return loginUser;
    }

    /**
     * test check connection
     */
    @Test
    public void testCheckConnection() throws Exception {
        DbType dataSourceType = DbType.POSTGRESQL;
        String dataSourceName = "dataSource01";
        String dataSourceDesc = "test dataSource";

        PostgreSQLDataSourceParamDTO postgreSqlDatasourceParam = new PostgreSQLDataSourceParamDTO();
        postgreSqlDatasourceParam.setDatabase(dataSourceName);
        postgreSqlDatasourceParam.setNote(dataSourceDesc);
        postgreSqlDatasourceParam.setHost("172.16.133.200");
        postgreSqlDatasourceParam.setPort(5432);
        postgreSqlDatasourceParam.setDatabase("dolphinscheduler");
        postgreSqlDatasourceParam.setUserName("postgres");
        postgreSqlDatasourceParam.setPassword("");
        ConnectionParam connectionParam = DataSourceUtils.buildConnectionParams(postgreSqlDatasourceParam);

        try (
                MockedStatic<DataSourceUtils> mockedStaticDataSourceClientProvider =
                        Mockito.mockStatic(DataSourceUtils.class)) {
            DataSourceProcessor dataSourceProcessor = Mockito.mock(DataSourceProcessor.class);

            when(DataSourceUtils.getDatasourceProcessor(Mockito.any())).thenReturn(dataSourceProcessor);
            when(dataSourceProcessor.checkDataSourceConnectivity(Mockito.any())).thenReturn(false);

            assertThrowsServiceException(Status.CONNECTION_TEST_FAILURE,
                    () -> dataSourceService.checkConnection(dataSourceType, connectionParam));

            when(dataSourceProcessor.checkDataSourceConnectivity(Mockito.any())).thenReturn(true);
            assertDoesNotThrow(() -> dataSourceService.checkConnection(dataSourceType, connectionParam));
        }
    }

    @Test
    public void testGetDatabases() throws SQLException {
        DataSource dataSource = getOracleDataSource();
        int datasourceId = 1;
        dataSource.setId(datasourceId);
        when(dataSourceMapper.selectById(datasourceId)).thenReturn(null);

        try {
            dataSourceService.getDatabases(datasourceId);
        } catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains(Status.QUERY_DATASOURCE_ERROR.getMsg()));
        }

        when(dataSourceMapper.selectById(datasourceId)).thenReturn(dataSource);
        MySQLConnectionParam connectionParam = new MySQLConnectionParam();
        Connection connection = Mockito.mock(Connection.class);
        MockedStatic<DataSourceUtils> dataSourceUtils = Mockito.mockStatic(DataSourceUtils.class);
        dataSourceUtils.when(() -> DataSourceUtils.getConnection(Mockito.any(), Mockito.any())).thenReturn(connection);
        dataSourceUtils.when(() -> DataSourceUtils.buildConnectionParams(Mockito.any(), Mockito.any()))
                .thenReturn(connectionParam);

        try {
            dataSourceService.getDatabases(datasourceId);
        } catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains(Status.GET_DATASOURCE_TABLES_ERROR.getMsg()));
        }

        dataSourceUtils.when(() -> DataSourceUtils.buildConnectionParams(Mockito.any(), Mockito.any()))
                .thenReturn(null);

        try {
            dataSourceService.getDatabases(datasourceId);
        } catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains(Status.DATASOURCE_CONNECT_FAILED.getMsg()));
        }
        connection.close();
        dataSourceUtils.close();
    }
}
