package brooklyn.location.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

public class CloudMachineNamerTest {

    private static final Logger log = LoggerFactory.getLogger(CloudMachineNamerTest.class);
    
    private TestApplication app;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testGenerateGroupIdWithEntity() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"));
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));

        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);

        String result = new CloudMachineNamer(cfg).generateNewGroupId();

        log.info("test entity child group id gives: "+result);
        // e.g. brooklyn-alex-tistapp-uube-testent-xisg-rwad
        Assert.assertTrue(result.length() <= 60);

        String user = Strings.maxlen(System.getProperty("user.name"), 4).toLowerCase();
        Assert.assertTrue(result.indexOf(user) >= 0);
        Assert.assertTrue(result.indexOf("-tistapp-") >= 0);
        Assert.assertTrue(result.indexOf("-testent-") >= 0);
        Assert.assertTrue(result.indexOf("-"+Strings.maxlen(app.getId(), 4).toLowerCase()) >= 0);
        Assert.assertTrue(result.indexOf("-"+Strings.maxlen(child.getId(), 4).toLowerCase()) >= 0);
    }
    
    @Test
    public void testGenerateNewMachineName() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"));
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));

        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
        CloudMachineNamer namer = new CloudMachineNamer(cfg);
        
        String result = namer.generateNewMachineUniqueName();
        Assert.assertTrue(result.length() <= namer.getMaxNameLength());
        String user = Strings.maxlen(System.getProperty("user.name"), 4).toLowerCase();
        Assert.assertTrue(result.indexOf(user) >= 0);
        Assert.assertTrue(result.indexOf("-tistapp-") >= 0);
        Assert.assertTrue(result.indexOf("-testent-") >= 0);
        Assert.assertTrue(result.indexOf("-"+Strings.maxlen(app.getId(), 4).toLowerCase()) >= 0);
        Assert.assertTrue(result.indexOf("-"+Strings.maxlen(child.getId(), 4).toLowerCase()) >= 0);
    }
    
    @Test
    public void testGenerateNewMachineUniqueNameFromGroupId() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"));
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));

        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
        CloudMachineNamer namer = new CloudMachineNamer(cfg);
        
        String groupId = namer.generateNewGroupId();
        String result = namer.generateNewMachineUniqueNameFromGroupId(groupId);
        Assert.assertTrue(result.startsWith(groupId));
        Assert.assertTrue(result.length() == groupId.length() + 5);
    }
    
    @Test
    public void testLengthMaxPermittedForMachineName() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"));
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));
        
        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
        CloudMachineNamer namer = new CloudMachineNamer(cfg);
        namer.lengthMaxPermittedForMachineName(10);
        String result = namer.generateNewMachineUniqueName();
        Assert.assertEquals(result.length(), 10);
    }
    
    @Test
    public void testLengthReserverdForNameInGroup() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"));
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));
        
        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
        CloudMachineNamer namer = new CloudMachineNamer(cfg);
        namer.lengthMaxPermittedForMachineName(10);
        namer.lengthReservedForNameInGroup(4);
        String groupId = namer.generateNewGroupId();
        Assert.assertEquals(6, groupId.length());
    }

    @Test
    public void testSanitize() {
        Assert.assertEquals(
                CloudMachineNamer.sanitize("me & you like _underscores but not !!! or dots...dots...dots"),
                "me-you-like-_underscores-but-not-or-dots-dots-dots"
            );
    }
}
