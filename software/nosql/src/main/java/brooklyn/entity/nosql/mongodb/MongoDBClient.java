package brooklyn.entity.nosql.mongodb;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.nosql.mongodb.sharding.MongoDBShardedDeployment;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(MongoDBClientImpl.class)
public interface MongoDBClient extends AbstractMongoDBServer {
    
    @SetFromFlag("shardedDeployment")
    ConfigKey<MongoDBShardedDeployment> SHARDED_DEPLOYMENT = ConfigKeys.newConfigKey(MongoDBShardedDeployment.class, 
            "mongodb.colocatedrouter.shardeddeployment", "Sharded deployment that the client will use to run scripts");

}
