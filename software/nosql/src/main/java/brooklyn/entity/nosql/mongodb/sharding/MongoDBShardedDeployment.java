package brooklyn.entity.nosql.mongodb.sharding;

import java.util.List;
import java.util.Map;

import com.google.common.reflect.TypeToken;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.nosql.mongodb.AbstractMongoDBServer;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

@ImplementedBy(MongoDBShardedDeploymentImpl.class)
public interface MongoDBShardedDeployment extends Entity, Startable {
    @SetFromFlag("configClusterSize")
    ConfigKey<Integer> CONFIG_CLUSTER_SIZE = ConfigKeys.newIntegerConfigKey("mongodb.config.cluster.size", 
            "Number of config servers", 3);
    
    @SetFromFlag("initialRouterClusterSize")
    ConfigKey<Integer> INITIAL_ROUTER_CLUSTER_SIZE = ConfigKeys.newIntegerConfigKey("mongodb.router.cluster.initial.size", 
            "Initial number of routers (mongos)", 0);
    
    @SetFromFlag("initialShardClusterSize")
    ConfigKey<Integer> INITIAL_SHARD_CLUSTER_SIZE = ConfigKeys.newIntegerConfigKey("mongodb.shard.cluster.initial.size", 
            "Initial number of shards (replicasets)", 2);
    
    @SetFromFlag("shardReplicaSetSize")
    ConfigKey<Integer> SHARD_REPLICASET_SIZE = ConfigKeys.newIntegerConfigKey("mongodb.shard.replicaset.size", 
            "Number of servers (mongod) in each shard (replicaset)", 3);
    
    @SetFromFlag("routerUpTimeout")
    ConfigKey<Duration> ROUTER_UP_TIMEOUT = ConfigKeys.newConfigKey(Duration.class, "mongodb.router.up.timeout", 
            "Maximum time to wait for the routers to become available before adding the shards", Duration.FIVE_MINUTES);
    
    @SetFromFlag("coLocatedRouterGroup")
    ConfigKey<Group> CO_LOCATED_ROUTER_GROUP = ConfigKeys.newConfigKey(Group.class, "mongodb.colocated.router.group", 
            "Group to be monitored for the addition of new CoLocatedMongoDBRouter entities");
    
    @SetFromFlag("defaultScripts")
    ConfigKey<List<String>> DEFAULT_SCRIPTS = AbstractMongoDBServer.DEFAULT_SCRIPTS;
    
    @SetFromFlag("scripts")
    ConfigKey<Map<String, String>> SCRIPTS = AbstractMongoDBServer.SCRIPTS;
    
    public static AttributeSensor<MongoDBConfigServerCluster> CONFIG_SERVER_CLUSTER = Sensors.newSensor(
            MongoDBConfigServerCluster.class, "mongodbshardeddeployment.configservers", "Config servers");
    public static AttributeSensor<MongoDBRouterCluster> ROUTER_CLUSTER = Sensors.newSensor(
            MongoDBRouterCluster.class, "mongodbshardeddeployment.routers", "Routers");
    
    public static AttributeSensor<MongoDBShardCluster> SHARD_CLUSTER = Sensors.newSensor(
            MongoDBShardCluster.class, "mongodbshardeddeployment.shards", "Shards");
    
    MethodEffector<Void> RUN_SCRIPT = new MethodEffector<Void>(MongoDBShardedDeployment.class, "runScript");
    
    @Effector(description="Runs one of the scripts defined in mongodb.client.scripts")
    void runScript(@EffectorParam(name="script name", description="Name of the script as defined in mongodb.scripts") String scriptName);
    
    public MongoDBConfigServerCluster getConfigCluster();
    
    public MongoDBRouterCluster getRouterCluster();
    
    public MongoDBShardCluster getShardCluster();
}
