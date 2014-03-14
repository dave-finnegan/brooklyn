package brooklyn.entity.nosql.mongodb;

import java.util.Map;

import com.google.common.base.Predicate;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.nosql.mongodb.sharding.MongoDBRouter;
import brooklyn.entity.nosql.mongodb.sharding.MongoDBRouterCluster;
import brooklyn.entity.nosql.mongodb.sharding.MongoDBShardedDeployment;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;

public class MongoDBClientSshDriver extends AbstractMongoDBSshDriver implements MongoDBClientDriver {
    
    private boolean isRunning = false;

    public MongoDBClientSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    @Override
    public void customize() {
        String command = String.format("mkdir -p %s", getRunDir());
        newScript(LAUNCHING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(command).execute();
        Map<String, String> scripts = entity.getConfig(MongoDBClient.SCRIPTS);
        for (String scriptName : scripts.keySet()) {
            copyResource(scripts.get(scriptName), getRunDir() + "/" + scriptName + ".js");
        }
    }

    @Override
    public void launch() {
        runStartupScripts();
        isRunning = true;
    }
    
    @Override
    public boolean isRunning() {
        return isRunning;
    }
    
    @Override
    protected AbstractMongoDBServer getServer() {
        MongoDBRouter router;
        MongoDBShardedDeployment deployment = entity.getConfig(MongoDBClient.SHARDED_DEPLOYMENT);
        Task<MongoDBRouter> task = DependentConfiguration.attributeWhenReady(deployment.getRouterCluster(), MongoDBRouterCluster.ANY_ROUTER);
        try {
            router = DependentConfiguration.waitForTask(task, entity, "any available router");
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        DependentConfiguration.waitInTaskForAttributeReady(router, MongoDBRouter.SHARD_COUNT, new Predicate<Integer>() {
            public boolean apply(Integer input) {
                return input > 0;
            };
        });
        return router;
    }

}
