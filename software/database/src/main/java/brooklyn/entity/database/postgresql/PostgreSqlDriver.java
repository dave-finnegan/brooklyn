package brooklyn.entity.database.postgresql;

import brooklyn.entity.basic.SoftwareProcessDriver;
import brooklyn.util.task.system.ProcessTaskWrapper;

/**
 * The {@link brooklyn.entity.basic.SoftwareProcessDriver} for PostgreSQL.
 */
public interface PostgreSqlDriver extends SoftwareProcessDriver {

    String getStatusCmd();

    ProcessTaskWrapper<Integer> executeScriptAsync(String commands);
}
