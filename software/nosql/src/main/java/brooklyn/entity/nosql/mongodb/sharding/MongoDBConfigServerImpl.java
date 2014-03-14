package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.nosql.mongodb.AbstractMongoDBDriver;

public class MongoDBConfigServerImpl extends SoftwareProcessImpl implements MongoDBConfigServer {

    @Override
    public Class<?> getDriverInterface() {
        return MongoDBConfigServerDriver.class;
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    @Override
    public void runScript(String scriptName) {
        ((AbstractMongoDBDriver)getDriver()).runScript(scriptName);
    }

}
