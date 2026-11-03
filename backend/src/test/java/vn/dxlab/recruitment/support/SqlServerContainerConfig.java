package vn.dxlab.recruitment.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQL Server thật trong Docker cho test tích hợp. Mỗi JVM test dựng một container,
 * Flyway chạy toàn bộ migration từ V1 trên database trống.
 *
 * <p>Collation đặt ở cấp database (giống infra/mssql/init-db.sql), không đặt
 * {@code MSSQL_COLLATION} cho cả server: trên SQL Server for Linux biến đó làm hỏng
 * {@code xp_userlock}, khiến Flyway không lấy được khóa migration.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SqlServerContainerConfig {

    // Ghim bản CU cụ thể: kết quả test giống nhau trên mọi máy, không đổi theo tag "latest".
    private static final DockerImageName IMAGE = DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04");
    private static final String DATABASE = "dxlab_test";

    @Bean
    MSSQLServerContainer sqlServer() {
        return new MSSQLServerContainer(IMAGE).acceptLicense();
    }

    @Bean
    DynamicPropertyRegistrar sqlServerProperties(MSSQLServerContainer sqlServer) {
        return registry -> {
            registry.add("spring.datasource.url", () -> {
                createDatabase(sqlServer);
                return sqlServer.getJdbcUrl() + ";databaseName=" + DATABASE;
            });
            registry.add("spring.datasource.username", sqlServer::getUsername);
            registry.add("spring.datasource.password", sqlServer::getPassword);
        };
    }

    private static void createDatabase(MSSQLServerContainer sqlServer) {
        String sql = "IF DB_ID(N'" + DATABASE + "') IS NULL CREATE DATABASE " + DATABASE + " COLLATE Latin1_General_100_CI_AI";
        try (var conn = DriverManager.getConnection(sqlServer.getJdbcUrl(), sqlServer.getUsername(), sqlServer.getPassword());
             var stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Không tạo được database test " + DATABASE, e);
        }
    }
}
