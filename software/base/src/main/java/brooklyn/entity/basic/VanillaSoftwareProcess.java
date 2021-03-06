package brooklyn.entity.basic;

import brooklyn.config.ConfigKey;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;

/** 
 * downloads and unpacks the archive indicated (optionally), 
 * then runs the management commands (scripts) indicated
 * (relative to the root of the archive if supplied, otherwise in a tmp working dir) to manage
 * <p>
 * uses config keys to identify the files / commands to use
 * <p>
 * in simplest mode, simply provide either:
 * <li> an archive in {@link #DOWNLOAD_URL} containing a <code>./start.sh</code>
 * <li> a start command to invoke in {@link #LAUNCH_COMMAND}
 * <p>
 * the only constraint is that the start command must write the PID into the file pointed to by the injected environment variable PID_FILE,
 * unless one of the steps below is done.
 * <p>
 * the start command can be a complex bash command, downloading and unpacking files, and/or handling the PID_FILE requirement 
 * (e.g. <code>export MY_PID_FILE=$PID_FILE ; ./my_start.sh</code> or <code>nohup ./start.sh & ; echo $! > $PID_FILE ; sleep 5</code>),
 * and of course you can supply both {@link #DOWNLOAD_URL} and {@link #LAUNCH_COMMAND}.
 * <p>
 * TODO:
 * by default the PID is used to stop the process (kill followed by kill -9 if needed) and restart (process stop followed by process start),
 * but we could instead supply (through config keys)
 * <li> a custom CHECK_RUNNING_COMMAND 
 * <li> a custom STOP_COMMAND
 * <li> or specify a PID_FILE to use (done)
 */
@ImplementedBy(VanillaSoftwareProcessImpl.class)
public interface VanillaSoftwareProcess extends SoftwareProcess {
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = SoftwareProcess.DOWNLOAD_URL;
    ConfigKey<String> LAUNCH_COMMAND = ConfigKeys.newStringConfigKey("launch.command", "command to run to launch the process", "./start.sh");
    // TODO CHECK_RUNNING_COMMAND STOP_COMMAND PID_FILE(templated config key and attribute?)
    
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.0.0");
}