package brooklyn.entity.nosql.mongodb;

import java.util.List;
import java.util.Map;

import com.google.common.reflect.TypeToken;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface AbstractMongoDBServer extends SoftwareProcess, Entity {

    @SetFromFlag("dataDirectory")
    ConfigKey<String> DATA_DIRECTORY = ConfigKeys.newStringConfigKey(
            "mongodb.data.directory", "Data directory to store MongoDB journals");
    
    @SetFromFlag("mongodbConfTemplateUrl")
    ConfigKey<String> MONGODB_CONF_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "mongodb.config.url", "Template file (in freemarker format) for a MongoDB configuration file",
            "classpath://brooklyn/entity/nosql/mongodb/default-mongodb.conf");
    
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "2.5.4");

    @SuppressWarnings("serial")
    @SetFromFlag("defaultScripts")
    ConfigKey<List<String>> DEFAULT_SCRIPTS = ConfigKeys.newConfigKey(
            new TypeToken<List<String>>(){}, "mongodb.defaultScripts", 
            "List of scripts defined in mongodb.scripts to be run on startup");
    
    @SuppressWarnings("serial")
    @SetFromFlag("scripts")
    ConfigKey<Map<String, String>> SCRIPTS = ConfigKeys.newConfigKey(
            new TypeToken<Map<String, String>>(){}, "mongodb.scripts", "List of javascript scripts to be copied "
                    + "to the server. These scripts can be run using the runScript effector");
    
    // e.g. http://fastdl.mongodb.org/linux/mongodb-linux-x86_64-2.2.2.tgz,
    // http://fastdl.mongodb.org/osx/mongodb-osx-x86_64-2.2.2.tgz
    // http://downloads.mongodb.org/win32/mongodb-win32-x86_64-1.8.5.zip
    // Note Windows download is a zip.
    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://fastdl.mongodb.org/${driver.osDir}/${driver.osTag}-${version}.tgz");

    @SetFromFlag("port")
    PortAttributeSensorAndConfigKey PORT =
            new PortAttributeSensorAndConfigKey("mongodb.server.port", "Server port", "27017+");
    
    MethodEffector<Void> RUN_SCRIPT = new MethodEffector<Void>(AbstractMongoDBServer.class, "runScript");
    
    @Effector(description="Runs one of the scripts defined in mongodb.client.scripts")
    void runScript(@EffectorParam(name="script name", description="Name of the script as defined in mongodb.scripts") String scriptName);
}