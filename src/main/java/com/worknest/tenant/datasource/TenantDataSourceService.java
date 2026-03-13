package com.worknest.tenant.datasource;

import com.worknest.master.entity.PlatformTenant;

import javax.sql.DataSource;
import java.util.Map;

public interface TenantDataSourceService {

    DataSource getDataSource(String tenantKey);

    Map<String, DataSource> getAllDataSources();

    void removeDataSource(String tenantKey);

    DataSource createDataSource(PlatformTenant tenant);
}

