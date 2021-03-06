package brooklyn.policy.ha;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Ticker;
import com.google.common.collect.Lists;

/** attaches to a DynamicCluster and replaces a failed member in response to HASensors.ENTITY_FAILED or other sensor;
 * if this fails, it sets the Cluster state to on-fire */
public class ServiceReplacer extends AbstractPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceReplacer.class);

    // TODO if there are multiple failures perhaps we should abort quickly
    
    public static final BasicNotificationSensor<FailureDescriptor> ENTITY_REPLACEMENT_FAILED = new BasicNotificationSensor<FailureDescriptor>(
            FailureDescriptor.class, "ha.entityFailed.replacement", "Indicates that an entity replacement attempt has failed");

    @SetFromFlag("setOnFireOnFailure")
    public static final ConfigKey<Boolean> SET_ON_FIRE_ON_FAILURE = ConfigKeys.newBooleanConfigKey("setOnFireOnFailure", "", true);
    
    /** monitors this sensor, by default ENTITY_RESTART_FAILED */
    @SetFromFlag("failureSensorToMonitor")
    @SuppressWarnings("rawtypes")
    public static final ConfigKey<Sensor> FAILURE_SENSOR_TO_MONITOR = new BasicConfigKey<Sensor>(Sensor.class, "failureSensorToMonitor", "", ServiceRestarter.ENTITY_RESTART_FAILED); 

    /** skips replace if replacement has failed this many times failure re-occurs within this time interval */
    @SetFromFlag("failOnRecurringFailuresInThisDuration")
    public static final ConfigKey<Long> FAIL_ON_RECURRING_FAILURES_IN_THIS_DURATION = ConfigKeys.newLongConfigKey(
            "failOnRecurringFailuresInThisDuration", 
            "abandon replace if replacement has failed many times within this time interval",
            5*60*1000L);

    /** skips replace if replacement has failed this many times failure re-occurs within this time interval */
    @SetFromFlag("failOnNumRecurringFailures")
    public static final ConfigKey<Integer> FAIL_ON_NUM_RECURRING_FAILURES = ConfigKeys.newIntegerConfigKey(
            "failOnNumRecurringFailures", 
            "abandon replace if replacement has failed this many times (100% of attempts) within the time interval",
            5);

    @SetFromFlag("ticker")
    public static final ConfigKey<Ticker> TICKER = ConfigKeys.newConfigKey(Ticker.class,
            "ticker", 
            "A time source (defaults to system-clock, which is almost certainly what's wanted, except in tests)",
            null);

    protected final List<Long> consecutiveReplacementFailureTimes = Lists.newCopyOnWriteArrayList();
    
    public ServiceReplacer() {
        this(new ConfigBag());
    }
    
    public ServiceReplacer(Map<String,?> flags) {
        this(new ConfigBag().putAll(flags));
    }
    
    public ServiceReplacer(ConfigBag configBag) {
        // TODO hierarchy should use ConfigBag, and not change flags
        super(configBag.getAllConfigMutable());
    }
    
    public ServiceReplacer(Sensor<?> failureSensorToMonitor) {
        this(new ConfigBag().configure(FAILURE_SENSOR_TO_MONITOR, failureSensorToMonitor));
    }

    @Override
    public void setEntity(EntityLocal entity) {
        checkArgument(entity instanceof DynamicCluster, "Replacer must take a DynamicCluster, not %s", entity);
        Sensor<?> failureSensorToMonitor = checkNotNull(getConfig(FAILURE_SENSOR_TO_MONITOR), "failureSensorToMonitor");
        
        super.setEntity(entity);

        subscribeToMembers((Group)entity, failureSensorToMonitor, new SensorEventListener<Object>() {
                @Override public void onEvent(SensorEvent<Object> event) {
                    onDetectedFailure(event);
                }
            });
    }
    
    // TODO semaphores would be better to allow at-most-one-blocking behaviour
    protected synchronized void onDetectedFailure(final SensorEvent<Object> event) {
        if (isSuspended()) {
            LOG.warn("ServiceReplacer suspended, so not acting on failure detected at "+event.getSource()+" ("+event.getValue()+", child of "+entity+")");
            return;
        }

        if (isRepeatedlyFailingTooMuch()) {
            LOG.error("ServiceReplacer not acting on failure detected at "+event.getSource()+" ("+event.getValue()+", child of "+entity+"), because too many recent replacement failures");
            return;
        }
        
        LOG.warn("ServiceReplacer acting on failure detected at "+event.getSource()+" ("+event.getValue()+", child of "+entity+")");
        ((EntityInternal)entity).getManagementSupport().getExecutionContext().submit(MutableMap.of(), new Runnable() {

            @Override
            public void run() {
                try {
                    Entities.invokeEffectorWithArgs(entity, entity, DynamicCluster.REPLACE_MEMBER, event.getSource().getId()).get();
                    consecutiveReplacementFailureTimes.clear();
                } catch (Exception e) {
                    // FIXME replaceMember fails if stop fails on the old node; should resolve that more gracefully than this
                    if (e.toString().contains("stopping") && e.toString().contains(event.getSource().getId())) {
                        LOG.info("ServiceReplacer: ignoring error reported from stopping failed node "+event.getSource());
                        return;
                    }

                    onReplacementFailed("Replace failure (error "+e+") at "+entity+": "+event.getValue());
                }

            }

        });
    }

    private boolean isRepeatedlyFailingTooMuch() {
        Integer failOnNumRecurringFailures = getConfig(FAIL_ON_NUM_RECURRING_FAILURES);
        long failOnRecurringFailuresInThisDuration = getConfig(FAIL_ON_RECURRING_FAILURES_IN_THIS_DURATION);
        long oldestPermitted = currentTimeMillis() - failOnRecurringFailuresInThisDuration;
        
        // trim old ones
        for (Iterator<Long> iter = consecutiveReplacementFailureTimes.iterator(); iter.hasNext();) {
            Long timestamp = iter.next();
            if (timestamp < oldestPermitted) {
                iter.remove();
            } else {
                break;
            }
        }
        
        return (consecutiveReplacementFailureTimes.size() >= failOnNumRecurringFailures);
    }

    protected long currentTimeMillis() {
        Ticker ticker = getConfig(TICKER);
        return (ticker == null) ? System.currentTimeMillis() : TimeUnit.NANOSECONDS.toMillis(ticker.read());
    }
    
    protected void onReplacementFailed(String msg) {
        LOG.warn("ServiceReplacer failed for "+entity+": "+msg);
        consecutiveReplacementFailureTimes.add(currentTimeMillis());
        
        if (getConfig(SET_ON_FIRE_ON_FAILURE)) {
            entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
        }
        entity.emit(ENTITY_REPLACEMENT_FAILED, new FailureDescriptor(entity, msg));
    }
}
