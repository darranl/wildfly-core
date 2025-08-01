/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.scanner;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FULL_REPLACE_DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OWNER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PERSISTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REDEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLED_BACK;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEPLOY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.server.deployment.scanner.api.DeploymentOperations;
import org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests of {@link FileSystemDeploymentService}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@RunWith(BMUnitRunner.class)
public class FileSystemDeploymentServiceUnitTestCase {

    private static final Logger logger = Logger.getLogger(FileSystemDeploymentServiceUnitTestCase.class);

    private static long count = System.currentTimeMillis();

    private static final Random random = new Random(System.currentTimeMillis());

    private static final DiscardTaskExecutor executor = new DiscardTaskExecutor();

    private static final PathAddress resourceAddress = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, DeploymentScannerExtension.SUBSYSTEM_NAME),
            PathElement.pathElement(DeploymentScannerExtension.SCANNERS_PATH.getKey(), DeploymentScannerExtension.DEFAULT_SCANNER_NAME));

    private static AutoDeployTestSupport testSupport;
    private File tmpDir;

    @BeforeClass
    public static void createTestSupport() {
        testSupport = new AutoDeployTestSupport(FileSystemDeploymentServiceUnitTestCase.class.getSimpleName());
    }

    @AfterClass
    public static void cleanup() {
        if (testSupport != null) {
            testSupport.cleanupFiles();
        }
    }

    @Before
    public void setup() {
        executor.clear();

        File root = testSupport.getTempDir();
        for (int i = 0; i < 200; i++) {
            tmpDir = new File(root, String.valueOf(count++));
            if (!tmpDir.exists() && tmpDir.mkdirs()) {
                break;
            }
        }

        if (!tmpDir.exists()) {
            throw new RuntimeException("cannot create tmpDir");
        }
    }

    @After
    public void tearDown() {
        testSupport.cleanupChannels();
    }

    @Test
    public void testIgnoreNoMarker() throws Exception {
        File f1 = createFile("foo.war");
        TesteeSet ts = createTestee();
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
//        ts.controller.addGetDeploymentNamesResponse();
        ts.testee.scan();
        assertTrue(f1.exists());
        assertFalse(deployed.exists());
    }

    @Test
    public void testBasicDeploy() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
    }

    @Test
    public void testBasicXmlDeploy() throws Exception {
        File xml = createXmlFile("foo.xml", "<rootElement/>");
        File dodeploy = createFile("foo.xml" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.xml" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(xml.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
    }

    @Test
    public void testAutoXmlDeploy() throws Exception {
        File xml = createXmlFile("foo.xml", "<rootElement/>");
        File deployed = new File(tmpDir, "foo.xml" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.setAutoDeployXMLContent(true);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(xml.exists());
        assertTrue(deployed.exists());
    }


    /**
     * Tests that an incomplete XML deployment does not
     * auto-deploy.
     */
    @Test
    public void testIncompleteXmlDeployment() throws Exception {
        File xml = createXmlFile("foo.xml", "<rootElement><incomplete>");
        File deployed = new File(tmpDir, "foo.xml" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.setAutoDeployXMLContent(true);
        ts.testee.scan();
        assertTrue(xml.exists());
        assertFalse(deployed.exists());
    }

    @Test
    public void testNestedDeploy() throws Exception {
        TesteeSet ts = createTestee();
        File nestedDir = new File(tmpDir, "nested");
        File war = createFile(nestedDir, "foo.war");
        File dodeploy = createFile(nestedDir, "foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(nestedDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
    }

    @Test
    public void testTwoFileDeploy() throws Exception {
        File war1 = createFile("foo.war");
        File dodeploy1 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed1 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File war2 = createFile("bar.war");
        File dodeploy2 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed2 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(2);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war1.exists());
        assertFalse(dodeploy1.exists());
        assertTrue(deployed1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy2.exists());
        assertTrue(deployed2.exists());
    }

    @Test
    public void testBasicFailure() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeFailureResponse(1, 1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(failed.exists());
    }

    @Test
    public void testCompositeFailure() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee();
        ts.controller.addCompositeFailureResultResponse(2, 2);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(failed.exists());
    }

    @Test
    public void testTwoFileFailure() throws Exception {
        File war1 = createFile("foo.war");
        File dodeploy1 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed1 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed1 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        File war2 = createFile("bar.war");
        File dodeploy2 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed2 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.DEPLOYED);
        File failed2 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeFailureResponse(2, 2);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war1.exists());
        assertFalse(dodeploy1.exists());
        assertFalse(deployed1.exists());
        assertTrue(failed1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy2.exists());
        assertFalse(deployed2.exists());
        assertTrue(failed2.exists());
    }

    @Test
    // WFLY-364 Test a partial failure, where a runtime failure does not trigger a complete rollback
    public void testPartialCompositeFailure() throws Exception {
        File war1 = createFile("foo.war");
        File dodeploy1 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File war2 = createFile("bar.war");
        File dodeploy2 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
        TesteeSet ts = createTestee();

        ts.controller.addPartialCompositeFailureResultResponse(2, 2);
        ts.testee.scan();

        String[] list = tmpDir.list();
        assertNotNull(list);

        String deployed = null;
        String failed = null;

        for (final String name : list) {
            if (name.endsWith(FileSystemDeploymentService.DEPLOYED)) {
                Assert.assertNull(deployed);
                deployed = name.substring(0, name.length() - FileSystemDeploymentService.DEPLOYED.length());
            } else if (name.endsWith(FileSystemDeploymentService.FAILED_DEPLOY)) {
                Assert.assertNull(failed);
                failed = name.substring(0, name.length() - FileSystemDeploymentService.FAILED_DEPLOY.length());
            }
        }

        Assert.assertNotNull(deployed);
        Assert.assertNotNull(failed);

        assertTrue(war1.exists());
        assertFalse(dodeploy1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy2.exists());

        File deployed1 = new File(tmpDir, deployed + FileSystemDeploymentService.DEPLOYED);
        File failed1 = new File(tmpDir, deployed + FileSystemDeploymentService.FAILED_DEPLOY);
        File deployed2 = new File(tmpDir, failed + FileSystemDeploymentService.DEPLOYED);
        File failed2 = new File(tmpDir, failed + FileSystemDeploymentService.FAILED_DEPLOY);

        assertTrue(deployed1.getAbsolutePath(), deployed1.exists());
        assertFalse(failed1.getAbsolutePath(), failed1.exists());
        assertFalse(deployed2.exists());
        assertTrue(failed2.exists());

        dodeploy2 = createFile(failed + FileSystemDeploymentService.DO_DEPLOY);

        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        deployed2 = new File(tmpDir, failed + FileSystemDeploymentService.DEPLOYED);
        failed2 = new File(tmpDir, failed + FileSystemDeploymentService.FAILED_DEPLOY);

        assertFalse(dodeploy2.exists());
        assertTrue(deployed2.exists());
        assertFalse(failed2.exists());
    }

    @Test
    public void testCancellationDueToFailure() throws Exception {
        File war1 = createFile("bar.war");
        File dodeploy1 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed1 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.DEPLOYED);
        File failed1 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        File war2 = createFile("foo.war");
        File dodeploy2 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed2 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed2 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeFailureResponse(2, 1);
        // Retry fails as well
        ts.controller.addCompositeFailureResponse(1, 1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war1.exists());
        assertFalse(dodeploy1.exists());
        assertFalse(deployed1.exists());
        assertTrue(failed1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy2.exists());
        assertFalse(deployed2.exists());
        assertTrue(failed2.exists());
    }

    @Test
    public void testSuccessfulRetry() throws Exception {
        File war1 = createFile("bar.war");
        File dodeploy1 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed1 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.DEPLOYED);
        File failed1 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        File war2 = createFile("foo.war");
        File dodeploy2 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed2 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed2 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeFailureResponse(2, 1);
        // Retry succeeds
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy1.exists());
        assertFalse(dodeploy2.exists());
        assertFalse(deployed1.exists() && deployed2.exists());
        assertTrue(failed1.exists() || failed2.exists());
    }

    @Test
    public void testCleanSpuriousMarkers() throws Exception {
        File f1 = createFile("spurious" + FileSystemDeploymentService.DEPLOYED);
        File f2 = createFile(new File(tmpDir, "nested"), "nested" + FileSystemDeploymentService.DEPLOYED);
        File f3 = createFile("ok" + FileSystemDeploymentService.DEPLOYED);
        File f4 = createFile(new File(tmpDir, "nested"), "nested-ok" + FileSystemDeploymentService.DEPLOYED);
        File f5 = createFile("spurious" + FileSystemDeploymentService.FAILED_DEPLOY);
        File f6 = createFile(new File(tmpDir, "nested"), "nested" + FileSystemDeploymentService.FAILED_DEPLOY);
        File f7 = createFile("spurious" + FileSystemDeploymentService.DEPLOYING);
        File f8 = createFile(new File(tmpDir, "nested"), "nested" + FileSystemDeploymentService.DEPLOYING);
        File f9 = createFile("spurious" + FileSystemDeploymentService.UNDEPLOYING);
        File f10 = createFile(new File(tmpDir, "nested"), "nested" + FileSystemDeploymentService.UNDEPLOYING);
        File f11 = createFile("spurious" + FileSystemDeploymentService.PENDING);
        File f12 = createFile(new File(tmpDir, "nested"), "nested" + FileSystemDeploymentService.PENDING);

        File ok = createFile("ok");
        File nestedOK = createFile(new File(tmpDir, "nested"), "nested-ok");

        // We expect 2 requests for children names due to subdir "nested"
        MockServerController sc = new MockServerController("ok", "nested-ok");
//        sc.responses.add(sc.responses.get(0));
        TesteeSet ts = createTestee(sc);

        ts.testee.scan();

        // These should get cleaned during initial scan
        Assert.assertFalse(f1.exists());
        Assert.assertFalse(f2.exists());
        Assert.assertTrue(f3.exists());
        Assert.assertTrue(f4.exists());
        Assert.assertTrue(ok.exists());
        Assert.assertTrue(nestedOK.exists());

        // failed deployments should not be cleaned on initial scan - will retry
        Assert.assertTrue(f5.exists());
        Assert.assertTrue(f6.exists());

        // The others should get cleaned in a scan
        Assert.assertFalse(f7.exists());
        Assert.assertFalse(f8.exists());
        Assert.assertFalse(f9.exists());
        Assert.assertFalse(f10.exists());
        Assert.assertFalse(f11.exists());
        Assert.assertFalse(f12.exists());

        Assert.assertTrue(ok.exists());
        Assert.assertTrue(nestedOK.exists());

        f1 = createFile("spurious" + FileSystemDeploymentService.DEPLOYED);
        f2 = createFile(new File(tmpDir, "nested"), "nested" + FileSystemDeploymentService.DEPLOYED);

        // WFCORE-6255 added UndeployTask, so here needs to be added mocked response which serves as counter of requests in the processOp()
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        // Failed deployments should be cleaned on subsequent scans.
        Assert.assertFalse(f5.exists());
        Assert.assertFalse(f6.exists());

        Assert.assertFalse(f1.exists());
        Assert.assertFalse(f2.exists());

        Assert.assertTrue(ok.exists());
        Assert.assertTrue(nestedOK.exists());
    }

    @Test
    public void testOverridePreexisting() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee("foo.war");
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
    }

    @Test
    public void testRedeploy() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee("foo.war");
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
        byte[] bytes = ts.controller.deployed.get("foo.war");
        // Since AS7-431 the content is no longer managed
        //assertTrue(Arrays.equals(bytes, ts.repo.content.iterator().next()));

        dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
        byte[] newbytes = ts.controller.deployed.get("foo.war");
        assertFalse(Arrays.equals(newbytes, bytes));
    }

    @Test
    public void testTwoFileRedeploy() throws Exception {
        File war1 = createFile("foo.war");
        File dodeploy1 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed1 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File war2 = createFile("bar.war");
        File dodeploy2 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed2 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(2);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war1.exists());
        assertFalse(dodeploy1.exists());
        assertTrue(deployed1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy2.exists());
        assertTrue(deployed2.exists());

        dodeploy1 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        dodeploy2 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(2);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(4, ts.repo.content.size());
        assertTrue(war1.exists());
        assertFalse(dodeploy1.exists());
        assertTrue(deployed1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy2.exists());
        assertTrue(deployed2.exists());
    }

    @Test
    public void testFailedRedeploy() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee("foo.war");
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertFalse(failed.exists());

        dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeFailureResponse(1, 1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(failed.exists());
    }

    @Test
    public void testTwoFileFailedRedeploy() throws Exception {
        File war1 = createFile("bar.war");
        File dodeploy1 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed1 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.DEPLOYED);
        File failed1 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        File war2 = createFile("foo.war");
        File dodeploy2 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed2 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed2 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(2);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war1.exists());
        assertFalse(dodeploy1.exists());
        assertTrue(deployed1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy2.exists());
        assertTrue(deployed2.exists());

        dodeploy1 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        dodeploy2 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeFailureResponse(2, 1);
        // Retry fails as well
        ts.controller.addCompositeFailureResponse(1, 1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(4, ts.repo.content.size());
        assertTrue(war1.exists());
        assertFalse(dodeploy1.exists());
        assertFalse(deployed1.exists());
        assertTrue(failed1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy2.exists());
        assertFalse(deployed2.exists());
        assertTrue(failed2.exists());
    }

    @Test
    public void testFailedRedeployNoMarker() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);
        ts.controller.addCompositeFailureResponse(1, 1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(failed.exists());

        // New deployment won't work unless content timestamp is newer than failed marker, so...
        failed.setLastModified(0);

        testSupport.createZip(war, 0, false, false, true, false);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertFalse(failed.exists());

    }

    /**
     * Test for WFCORE-6255
     * It tests that the failed archives are undeployed
     */
    @Test
    public void testRemoveFailedDeployment() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);

        // failure of deploying the foo.war (must be ready before scan)
        ts.controller.addCompositeFailureResponse(1, 1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(failed.exists());

        assertTrue(war.delete());
        // success of undeploying failed foo.war from the server (must be ready before scan)
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertFalse(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertFalse(failed.exists());
        assertEquals(0, ts.controller.added.size());
        assertEquals(0, ts.controller.deployed.size());
        assertTrue(ts.testee.getScanContextRegisteredDeployments().containsKey("foo.war"));

        // failed deployment is released from registeredDeployments at the beginning of the next scan (schedule rescan)
        ts.testee.scan();
        assertEquals(0, ts.testee.getScanContextRegisteredDeployments().size());
    }

    /**
     * After WFCORE-6255 we need a test that validates that the deployment not associated with the scanner being tested isn't undeployed because
     * a FAILED_DEPLOY marker file is left in the scanner's directory
     */
    @Test
    public void testIgnoreExternalScannerFailedDeployment() {
        MockServerController sc = new MockServerController("foo.war");
        TesteeSet ts = createTestee(sc);
        ts.controller.externallyDeployed.put("foo.war", null);
        assertThat(ts.controller.deployed.size(), is(1));
        ts.testee.bootTimeScan(sc.create());
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);

        // there is no need for mocked method addCompositeSuccessResponse associated with UndeployTask that means
        // that there is no undeployment attempt
        ts.testee.scan();

        assertFalse(failed.exists());
        assertThat(ts.controller.deployed.size(), is(1));
        assertThat(ts.controller.deployed.keySet(), hasItems("foo.war"));
    }

    @Test
    public void testSuccessfulRetryRedeploy() throws Exception {
        File war1 = createFile("bar.war");
        File dodeploy1 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed1 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.DEPLOYED);
        File failed1 = new File(tmpDir, "bar.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        File war2 = createFile("foo.war");
        File dodeploy2 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed2 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed2 = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(2);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war1.exists());
        assertFalse(dodeploy1.exists());
        assertTrue(deployed1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy2.exists());
        assertTrue(deployed2.exists());

        dodeploy1 = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        dodeploy2 = createFile("bar.war" + FileSystemDeploymentService.DO_DEPLOY);
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeFailureResponse(2, 1);
        // Retry succeeds
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(4, ts.repo.content.size());
        assertTrue(war1.exists());
        assertTrue(war2.exists());
        assertFalse(dodeploy1.exists());
        assertFalse(dodeploy2.exists());
        assertFalse(deployed1.exists() && deployed2.exists());
        assertTrue(failed1.exists() || failed2.exists());
    }

    @Test
    public void testUndeployByMarkerDeletion() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());

        assertTrue(deployed.delete());
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertEquals(0, ts.controller.added.size());
        assertEquals(0, ts.controller.deployed.size());
    }

    @Test
    public void testUndeployByContentDeletionZipped() throws Exception {
        undeployByContentDeletionZippedTest(tmpDir);
    }

    @Test
    public void testUndeployContentInSubdirectory() throws Exception {
        File subdir = createDirectory(tmpDir, "sub");
        undeployByContentDeletionZippedTest(subdir);
    }

    private void undeployByContentDeletionZippedTest(File baseDir) throws Exception {
        // First, zipped content

        File war = createFile(baseDir, "foo.war");
        File dodeploy = createFile(baseDir, "foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(baseDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File undeployed = new File(baseDir, "foo.war" + FileSystemDeploymentService.UNDEPLOYED);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertFalse(undeployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());

        assertTrue(war.delete());
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertFalse(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(undeployed.exists());
        assertEquals(0, ts.controller.added.size());
        assertEquals(0, ts.controller.deployed.size());

        // Next, zipped content with auto-deploy disabled

        war = createFile(baseDir, "foo.war");
        dodeploy = createFile(baseDir, "foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        deployed = new File(baseDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        undeployed = new File(baseDir, "foo.war" + FileSystemDeploymentService.UNDEPLOYED);
        ts = createTestee();
        ts.testee.setAutoDeployZippedContent(false);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertFalse(undeployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());

        assertTrue(war.delete());
        ts.testee.scan();
        assertFalse(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertFalse(undeployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
    }

    @Test
    public void testUndeployByContentDeletionExploded() throws Exception {

        // First, auto-deploy enabled

        File war = new File(tmpDir, "foo.war");
        war.mkdirs();
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployExplodedContent(true);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());

        assertTrue(war.delete());
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertFalse(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertEquals(0, ts.controller.added.size());
        assertEquals(0, ts.controller.deployed.size());

        // Next, with auto-deploy disabled

        war = new File(tmpDir, "foo.war");
        war.mkdirs();
        dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        ts = createTestee();
        ts.testee.setAutoDeployExplodedContent(false);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());

        assertTrue(war.delete());
        ts.testee.scan();
        assertFalse(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());

        // Now, exploded content

        war = new File(tmpDir, "foo.war");
        war.mkdirs();
        dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        ts = createTestee();
        ts.testee.setAutoDeployExplodedContent(true);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());

        assertTrue(war.delete());
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertFalse(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertEquals(0, ts.controller.added.size());
        assertEquals(0, ts.controller.deployed.size());
    }

    @Test
    public void testRedeployUndeploy() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
        byte[] bytes = ts.controller.deployed.get("foo.war");
        // Since AS7-431 the content is no longer managed
        //assertTrue(Arrays.equals(bytes, ts.repo.content.iterator().next()));

        dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
        byte[] newbytes = ts.controller.deployed.get("foo.war");
        assertFalse(Arrays.equals(newbytes, bytes));

        assertTrue(deployed.delete());
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(2, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertEquals(0, ts.controller.added.size());
        assertEquals(0, ts.controller.deployed.size());
    }

    @Test
    public void testRedeployUndeployedNoMarker() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File undeployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.UNDEPLOYED);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
        byte[] bytes = ts.controller.deployed.get("foo.war");

        assertTrue(deployed.delete());
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(undeployed.exists());
        assertEquals(0, ts.controller.added.size());
        assertEquals(0, ts.controller.deployed.size());

        // new content will be ignored unless it has a timestamp newer than .undeployed
        // so, rather than sleeping...
        undeployed.setLastModified(0);

        testSupport.createZip(war, 0, false, false, false, false);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertFalse(undeployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
        byte[] newbytes = ts.controller.deployed.get("foo.war");
        assertFalse(Arrays.equals(newbytes, bytes));
    }


    @Test
    public void testRedeployUndeployedEnabled() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File undeployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.UNDEPLOYED);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
        byte[] bytes = ts.controller.deployed.get("foo.war");

        assertTrue(deployed.delete());
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(undeployed.exists());
        assertEquals(0, ts.controller.added.size());
        assertEquals(0, ts.controller.deployed.size());

        ts.controller.added.put("foo.war", bytes);
        ts.controller.deployed.put("foo.war", bytes);
        //as .undeployed timestamp is after the war file timestamp, only the re-enabling of the deployment removes it

        testSupport.createZip(war, 0, false, false, false, false);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertFalse(undeployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
    }

    @Test
    public void testDirectory() throws Exception {
        final File war = createDirectory("foo.war", "index.html");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee("foo.war");
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());

        assertTrue(deployed.delete());
//        ts.controller.addGetDeploymentNamesResponse();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        // Since AS7-431 the content is no longer managed
        //assertEquals(1, ts.repo.content.size());
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());

    }

    @Test
    public void testUndeployDeployExternalDeployment() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File undeployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.UNDEPLOYED);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
        byte[] bytes = ts.controller.deployed.get("foo.war");

        //Undeploy externally
        assertTrue(undeployed.createNewFile());
        undeployed.setLastModified(war.lastModified());
        assertTrue(deployed.delete());
        ts.controller.deployed.clear();
        ts.controller.added.clear();

        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(undeployed.exists());

        //Deploy externally loaded deployment
        ts.controller.added.put("foo.war", bytes);
        ts.controller.deployed.put("foo.war", bytes);
        ts.controller.externallyDeployed.put("foo.war", null);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(undeployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
    }

    @Test
    public void testUndeployDeploy() throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File undeployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.UNDEPLOYED);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
        byte[] bytes = ts.controller.deployed.get("foo.war");

        //Undeploy externally
        assertTrue(undeployed.createNewFile());
        undeployed.setLastModified(war.lastModified());
        assertTrue(deployed.delete());
        ts.controller.deployed.clear();
        ts.controller.added.clear();

        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertFalse(deployed.exists());
        assertTrue(undeployed.exists());

        //Deploy externally the current deployment
        ts.controller.added.put("foo.war", bytes);
        ts.controller.deployed.put("foo.war", bytes);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertFalse(undeployed.exists());
        assertEquals(1, ts.controller.added.size());
        assertEquals(1, ts.controller.deployed.size());
    }

    /**
     * Tests that autodeploy does not happen by default.
     */
    @Test
    public void testNoDefaultAutoDeploy() throws Exception {

        File zip = new File(tmpDir, "foo.war");
        File zipdeployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File zippending = new File(tmpDir, "foo.war" + FileSystemDeploymentService.PENDING);
        testSupport.createZip(zip, 0, false, false, true, false);
        File exploded = new File(tmpDir, "exploded.ear");
        exploded.mkdirs();
        File explodeddeployed = new File(tmpDir, "exploded.war" + FileSystemDeploymentService.DEPLOYED);
        File explodedpending = new File(tmpDir, "exploded.war" + FileSystemDeploymentService.PENDING);

        TesteeSet ts = createTestee();

        ts.testee.scan();

        assertFalse(zipdeployed.exists());
        assertFalse(zippending.exists());
        assertFalse(explodeddeployed.exists());
        assertFalse(explodedpending.exists());

        assertTrue(zip.exists());
        assertTrue(exploded.exists());
    }

    /**
     * Tests that an incomplete zipped deployment does not auto-deploy.
     */
    @Test
    public void testIncompleteZipped() throws Exception {

        File incomplete = new File(tmpDir, "foo.war");
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File pending = new File(tmpDir, "foo.war" + FileSystemDeploymentService.PENDING);
        testSupport.createZip(incomplete, 0, false, true, true, false);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);

        ts.testee.scan();

        assertFalse(deployed.exists());
        assertTrue(pending.exists());

        incomplete.delete();
        testSupport.createZip(incomplete, 0, false, false, false, false);

        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        assertTrue(deployed.exists());
        assertFalse(pending.exists());
    }

    /**
     * Tests that an exploded deployment with an incomplete child
     * does not auto-deploy, but does auto-deploy when the child
     * is complete.
     */
    @Test
    public void testIncompleteExploded() throws Exception {
        File deployment = new File(tmpDir, "foo.ear");
        deployment.mkdirs();
        File deployed = new File(tmpDir, "foo.ear" + FileSystemDeploymentService.DEPLOYED);
        File pending = new File(tmpDir, "foo.ear" + FileSystemDeploymentService.PENDING);
        File incomplete = new File(deployment, "bar.war");
        testSupport.createZip(incomplete, 0, false, true, true, false);

        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployExplodedContent(true);

        ts.testee.scan();

        assertFalse(deployed.exists());
        assertTrue(pending.exists());

        incomplete.delete();
        testSupport.createZip(incomplete, 0, false, false, true, false);

        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        assertTrue(deployed.exists());
        assertFalse(pending.exists());

    }

    /**
     * Test that an incomplete deployment prevents changing other items
     */
    @Test
    public void testIncompleteBlocksComplete() throws Exception {

        File incomplete = new File(tmpDir, "foo.war");
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File pending = new File(tmpDir, "foo.war" + FileSystemDeploymentService.PENDING);
        testSupport.createZip(incomplete, 0, false, true, true, false);
        File complete = new File(tmpDir, "complete.jar");
        File completeDeployed = new File(tmpDir, "complete.jar" + FileSystemDeploymentService.DEPLOYED);
        File completePending = new File(tmpDir, "complete.jar" + FileSystemDeploymentService.PENDING);
        testSupport.createZip(complete, 0, false, false, true, false);

        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);

        ts.testee.scan();

        assertFalse(deployed.exists());
        assertFalse(completeDeployed.exists());
        assertTrue(pending.exists());
        assertTrue(completePending.exists());

        incomplete.delete();
        testSupport.createZip(incomplete, 0, false, false, false, false);

        ts.controller.addCompositeSuccessResponse(2);
        ts.testee.scan();

        assertTrue(deployed.exists());
        assertTrue(completeDeployed.exists());
        assertFalse(pending.exists());
        assertFalse(completePending.exists());

    }

    /**
     * Tests that an incomplete deployment that makes no progress gets a .failed marker
     */
    @Test
    public void testMaxNoProgress() throws Exception {

        File incomplete = new File(tmpDir, "foo.war");
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File pending = new File(tmpDir, "foo.war" + FileSystemDeploymentService.PENDING);
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        testSupport.createZip(incomplete, 0, false, true, true, false);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);
        ts.testee.setMaxNoProgress(-1); // trigger immediate failure

        ts.testee.scan();

        assertFalse(deployed.exists());
        assertTrue(failed.exists());
        assertFalse(pending.exists());

    }

    /**
     * Tests that non-archive/non-marker files are ignored
     */
    @Test
    public void testIgnoreNonArchive() throws Exception {

        File txt = createFile("foo.txt");
        File deployed = new File(tmpDir, "foo.txt" + FileSystemDeploymentService.DEPLOYED);
        File pending = new File(tmpDir, "foo.txt" + FileSystemDeploymentService.PENDING);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployExplodedContent(true);
        ts.testee.setAutoDeployZippedContent(true);
        ts.testee.scan();

        assertTrue(txt.exists());
        assertFalse(pending.exists());
        assertFalse(deployed.exists());
    }

    /**
     * Tests that non-archive files nested in an exploded deployment are ignored
     */
    @Test
    public void testIgnoreNonArchiveInExplodedDeployment() throws Exception {
        File deployment = new File(tmpDir, "foo.ear");
        deployment.mkdirs();
        File deployed = new File(tmpDir, "foo.ear" + FileSystemDeploymentService.DEPLOYED);
        File pending = new File(tmpDir, "foo.ear" + FileSystemDeploymentService.PENDING);
        File nonarchive = createFile(deployment, "index.html");

        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployExplodedContent(true);

        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        assertTrue(deployed.exists());
        assertFalse(pending.exists());
        assertTrue(nonarchive.exists());
    }

    /**
     * Tests that the .skipdeploy marker prevents auto-deploy
     */
    @Test
    public void testSkipDeploy() throws Exception {
        File deployment = new File(tmpDir, "foo.ear");
        deployment.mkdirs();
        File deployed = new File(tmpDir, "foo.ear" + FileSystemDeploymentService.DEPLOYED);
        File pending = new File(tmpDir, "foo.ear" + FileSystemDeploymentService.PENDING);
        File skip = createFile(tmpDir, "foo.ear" + FileSystemDeploymentService.SKIP_DEPLOY);

        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployExplodedContent(true);

        ts.testee.scan();

        assertFalse(deployed.exists());
        assertFalse(pending.exists());

        skip.delete();

        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        assertTrue(deployed.exists());
        assertFalse(pending.exists());

    }

    /**
     * Tests that an incompletely copied file with a nested jar
     * *and* extraneous leading bytes fails cleanly.
     */
    @Test
    public void testNonScannableZipped() throws Exception {

        File incomplete = new File(tmpDir, "foo.war");
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File pending = new File(tmpDir, "foo.war" + FileSystemDeploymentService.PENDING);
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        testSupport.createZip(incomplete, 1, false, true, true, false);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);

        ts.testee.scan();

        assertFalse(deployed.exists());
        assertTrue(failed.exists());
        assertFalse(pending.exists());
    }

    /**
     * Tests that a ZIP64 format autodeployed file fails cleanly.
     * If ZIP 64 support is added, this test should be revised.
     */
    @Test
    public void testZip64Zipped() throws Exception {

        File zip64 = new File(tmpDir, "foo.war");
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File pending = new File(tmpDir, "foo.war" + FileSystemDeploymentService.PENDING);
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        testSupport.createZip(zip64, 0, false, false, true, true);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);

        ts.testee.scan();

        assertFalse(deployed.exists());
        assertTrue(failed.exists());
        assertFalse(pending.exists());
    }

    /**
     * Test for JBAS-9226 -- deleting a .deployed marker does not result in a redeploy
     *
     * @throws Exception if there is a problem setting up the test infrastructure
     */
    @Test
    public void testIgnoreUndeployed() throws Exception {

        File war = new File(tmpDir, "foo.war");
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File undeployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.UNDEPLOYED);
        testSupport.createZip(war, 0, false, false, false, false);
        TesteeSet ts = createTestee();
        ts.testee.setAutoDeployZippedContent(true);

        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        assertTrue(deployed.exists());

        // Undeploy
        deployed.delete();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        assertTrue(undeployed.exists());
        assertFalse(deployed.exists());

        // Confirm it doesn't come back
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        assertTrue(undeployed.exists());
        assertFalse(deployed.exists());
    }

    @Test
    public void testDeploymentTimeout() throws Exception {
        File deployment = new File(tmpDir, "foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        testSupport.createZip(deployment, 0, false, false, true, true);

        TesteeSet ts = createTestee(new DiscardTaskExecutor() {
            @Override
            public <T> CompletableFuture<T> submit(Callable<T> tCallable) {
                return new TimeOutFuture<>(5, tCallable);
            }
        });

        ts.testee.setDeploymentTimeout(5);

        ts.testee.scan();

        assertFalse(deployed.exists());
        assertFalse(dodeploy.exists());
        assertTrue(failed.exists());
    }

    @Test
    public void testArchiveRedeployedAfterReboot() throws Exception {

        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());

        // Create a new testee to simulate a reboot
        ts = createTestee();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
    }

    @Test
    public void testExplodedRedeployedAfterReboot() throws Exception {
        final File war = createDirectory("foo.war", "index.html");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        TesteeSet ts = createTestee();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());

        // Create a new testee to simulate a reboot
        ts = createTestee();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
    }

    /**
     * Tests that a deployment which had failed earlier, is redeployed (i.e. picked for deployment) when the deployment
     * file is updated (i.e. timestamp changes).
     *
     * @throws Exception if there is a problem setting up test infrastructure
     */
    @Test
    public void testFailedArchiveRedeployedAfterDeploymentUpdate() throws Exception {
        final String warName = "helloworld.war";
        // create our deployment file
        File war = new File(tmpDir, warName);
        testSupport.createZip(war, 0, false, false, false, false);
        // trigger deployment
        TesteeSet ts = createTestee();
        ts.controller.addCompositeFailureResponse(1, 1);
        // set the auto-deploy of zipped content on the scanner
        ts.testee.setAutoDeployZippedContent(true);
        ts.testee.scan();

        // make sure the deployment exists after the scan
        assertTrue(war.getAbsolutePath() + " is missing after deployment", war.exists());

        // failed marker file
        File failed = new File(tmpDir, warName + FileSystemDeploymentService.FAILED_DEPLOY);
        // make sure the failed marker exists
        assertTrue(failed.getAbsolutePath() + " marker file not found after deployment", failed.exists());

        // Now update the timestamp of the deployment war and retrigger a deployment scan expecting it to process
        // the deployment war and create a deployed (a.k.a successful) deployment marker. This simulates the case where the
        // original deployment file is "fixed" and the deployment scanner should pick it up even in the presence of the
        // (previous) failed marker
        long newLastModifiedTime = failed.lastModified() + 1000;
        logger.info("Updating last modified time of war " + war + " from " + war.lastModified() + " to " + newLastModifiedTime + " to simulate changes to the deployment");
        war.setLastModified(newLastModifiedTime);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        // make sure the deployment exists after the scan
        assertTrue(war.getAbsolutePath() + " is missing after re-deployment", war.exists());
        // make sure the deployed marker exists
        File deployed = createFile(warName + FileSystemDeploymentService.DEPLOYED);
        assertTrue(deployed.getAbsolutePath() + " marker file not found after re-deployment", deployed.exists());
        // make sure the failed marker *no longer exists*
        failed = new File(tmpDir, warName + FileSystemDeploymentService.FAILED_DEPLOY);
        assertFalse(failed.getAbsolutePath() + " marker file (unexpectedly) exists after re-deployment", failed.exists());

    }

    /**
     * Tests that a deployment which had been undeployed earlier, is redeployed (i.e. picked for deployment) when the deployment
     * file is updated (i.e. timestamp changes).
     *
     * @throws Exception if there is a problem setting up the test infrastructure
     */
    @Test
    public void testUndeployedArchiveRedeployedAfterDeploymentUpdate() throws Exception {
        final String warName = "helloworld2.war";
        // create our deployment file
        File war = new File(tmpDir, warName);
        testSupport.createZip(war, 0, false, false, false, false);
        // trigger deployment
        TesteeSet ts = createTestee();
        ts.controller.addCompositeSuccessResponse(1);
        // set the auto-deploy of zipped content on the scanner
        ts.testee.setAutoDeployZippedContent(true);
        ts.testee.scan();

        // make sure the deployment exists after the scan
        assertTrue(war.getAbsolutePath() + " is missing after deployment", war.exists());

        // deployed marker file
        File deployed = new File(tmpDir, warName + FileSystemDeploymentService.DEPLOYED);
        // make sure the deployed marker exists
        assertTrue(deployed.getAbsolutePath() + " marker file not found after deployment", deployed.exists());

        // now trigger an undeployment by removing the "deployed" marker file
        assertTrue("Could not delete " + deployed.getAbsolutePath() + " marker file", deployed.delete());
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        // make sure undeployed marker exists
        File undeployed = new File(tmpDir, warName + FileSystemDeploymentService.UNDEPLOYED);
        assertTrue(undeployed.getAbsolutePath() + " marker file not found after undeployment", undeployed.exists());

        // Now update the timestamp of the deployment war and retrigger a deployment scan expecting it to process
        // the deployment war and create a deployed (a.k.a successful) deployment marker. This simulates the case where the
        // original deployment file is redeployed and the deployment scanner should pick it up even in the presence of the
        // (previous) undeployed marker
        long newLastModifiedTime = undeployed.lastModified() + 1000;
        logger.info("Updating last modified time of war " + war + " from " + war.lastModified() + " to " + newLastModifiedTime + " to simulate changes to the deployment");
        war.setLastModified(newLastModifiedTime);
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        // make sure the deployment exists after the scan
        assertTrue(war.getAbsolutePath() + " is missing after re-deployment", war.exists());
        // make sure the deployed marker exists
        deployed = createFile(warName + FileSystemDeploymentService.DEPLOYED);
        assertTrue(deployed.getAbsolutePath() + " marker file not found after re-deployment", deployed.exists());
        // make sure the undeployed marker *no longer exists*
        undeployed = new File(tmpDir, warName + FileSystemDeploymentService.UNDEPLOYED);
        assertFalse(undeployed.getAbsolutePath() + " marker file (unexpectedly) exists after re-deployment", undeployed.exists());

    }

    /** AS7-784 */
    @Test
    public void testUndeployedExternally() throws Exception {
        File f1 = createFile("undeployed" + FileSystemDeploymentService.DEPLOYED);
        File f2 = new File(tmpDir, "undeployed" + FileSystemDeploymentService.UNDEPLOYED);
        File f3 = createFile(new File(tmpDir, "nested"), "nested-undeployed" + FileSystemDeploymentService.DEPLOYED);
        File f4 = new File(new File(tmpDir, "nested"), "nested-undeployed" + FileSystemDeploymentService.UNDEPLOYED);
        File f5 = createFile("removed" + FileSystemDeploymentService.DEPLOYED);
        File f6 = new File(tmpDir, "removed" + FileSystemDeploymentService.UNDEPLOYED);
        File f7 = createFile(new File(tmpDir, "nested"), "nested-removed" + FileSystemDeploymentService.DEPLOYED);
        File f8 = new File(new File(tmpDir, "nested"), "nested-removed" + FileSystemDeploymentService.UNDEPLOYED);

        File undeployed = createFile("undeployed");
        File nestedUndeployed = createFile(new File(tmpDir, "nested"), "nested-undeployed");

        File removed = createFile("removed");
        File nestedRemoved = createFile(new File(tmpDir, "nested"), "nested-removed");

        // We expect 2 requests for children names due to subdir "nested"
        MockServerController sc = new MockServerController("undeployed", "nested-undeployed", "removed", "nested-removed");
//        sc.responses.add(sc.responses.get(0));
        TesteeSet ts = createTestee(sc);

        Assert.assertTrue(f1.exists());
        Assert.assertFalse(f2.exists());
        Assert.assertTrue(f3.exists());
        Assert.assertFalse(f4.exists());
        Assert.assertTrue(f5.exists());
        Assert.assertFalse(f6.exists());
        Assert.assertTrue(undeployed.exists());
        Assert.assertTrue(nestedUndeployed.exists());
        Assert.assertTrue(removed.exists());
        Assert.assertTrue(nestedRemoved.exists());

        // WFCORE-1579: before tampering with deployments, at least one scan must proceed so that deployment directory
        // content is established
        ts.testee.scan();

        // Simulate the CLI or console undeploying 2 deployments and removing 2
        sc.deployed.remove("undeployed");
        sc.deployed.remove("nested-undeployed");
        sc.added.remove("removed");
        sc.added.remove("nested-removed");
        sc.deployed.remove("removed");
        sc.deployed.remove("nested-removed");

        ts.testee.scan();

        Assert.assertFalse(f1.exists());
        Assert.assertTrue(f2.exists());
        Assert.assertFalse(f3.exists());
        Assert.assertTrue(f4.exists());
        Assert.assertFalse(f5.exists());
        Assert.assertTrue(f6.exists());
        Assert.assertTrue(undeployed.exists());
        Assert.assertTrue(nestedUndeployed.exists());
        Assert.assertTrue(removed.exists());
        Assert.assertTrue(nestedRemoved.exists());
        Assert.assertEquals(0, sc.deployed.size());

        ts.testee.scan();

        Assert.assertFalse(f1.exists());
        Assert.assertTrue(f2.exists());
        Assert.assertFalse(f3.exists());
        Assert.assertTrue(f4.exists());
        Assert.assertFalse(f5.exists());
        Assert.assertTrue(f6.exists());
        Assert.assertTrue(undeployed.exists());
        Assert.assertTrue(nestedUndeployed.exists());
        Assert.assertTrue(removed.exists());
        Assert.assertTrue(nestedRemoved.exists());
        Assert.assertEquals(0, sc.deployed.size());
    }


    /** WFCORE-64 */
    @Test
    public void testForcedUndeployment() {
        MockServerController sc = new MockServerController("foo.war", "failure.ear");
        TesteeSet ts = createTestee(sc);
        ts.controller.externallyDeployed.put("foo.war", null);
        ts.controller.addCompositeSuccessResponse(1);
        assertThat(ts.controller.deployed.size(), is(2));
        //ts.testee.scan(true, new DefaultDeploymentOperations(sc), true);
        ts.testee.forcedUndeployScan();
        assertThat(ts.controller.deployed.size(), is(1)); //Only non-persistent deployments should be undeployed.
        assertThat(ts.controller.deployed.keySet(), hasItems("foo.war"));
    }

    @Test
    public void testUncleanShutdown() throws Exception {
        File deployment = new File(tmpDir, "foo.war");
        final File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        final File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        final File deploying = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYING);
        final DiscardTaskExecutor myWaitingExecutor = new DiscardTaskExecutor(true) {
            @Override
            public <T> CompletableFuture<T> submit(Callable<T> tCallable) {
                return new WaitingFuture<>(50000, tCallable);
            }
        };
        MockServerController sc = new MockServerController(myWaitingExecutor);
        final BlockingDeploymentOperations ops = new BlockingDeploymentOperations(sc.create());
        final DiscardTaskExecutor myExecutor = new DiscardTaskExecutor(true);
        final TesteeSet ts = createTestee(sc, myExecutor, ops);
        ts.testee.setAutoDeployZippedContent(true);
        sc.addCompositeSuccessResponse(1);
        testSupport.createZip(deployment, 0, false, false, true, true);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> lockDone = executorService.submit(new Callable<>() {
                @Override
                public Boolean call() {
                    try {
                        while (!ops.ready) {//Waiting for deployment to start.
                            Thread.sleep(100);
                        }
                        ts.testee.stopScanner();
                        return true;
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
            ts.testee.setScanInterval(10000);
            lockDone.get(60000, TimeUnit.MILLISECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
    * WFCORE-210
    */
    @BMRule(name = "Test IO failure",
            targetClass = "FileSystemProvider",
            targetMethod = "newDirectoryStream",
            targetLocation = "AT ENTRY",
            isOverriding = true,
            action = "throw new java.io.IOException(\"Thanks Byteman\")"
    )
    @Test
    public void testNoUndeployment() {
        MockServerController sc = new MockServerController("foo.war", "failure.ear");
        TesteeSet ts = createTestee(sc);
        ts.controller.externallyDeployed.put("foo.war", null);
        ts.controller.addCompositeSuccessResponse(1);
        assertThat(ts.controller.deployed.size(), is(2));
        try {
            ts.testee.bootTimeScan(sc.create());
            fail("RuntimeException expected");
        } catch (Exception ex) {
            assertThat(ex.getClass().isAssignableFrom(RuntimeException.class), is(true));
            assertThat(ex.getLocalizedMessage(), is(DeploymentScannerLogger.ROOT_LOGGER.cannotListDirectoryFiles(ex.getCause(), tmpDir).getLocalizedMessage()));
        }
        assertThat(ts.controller.deployed.size(), is(2)); //Only non-persistent deployments should be undeployed.
        assertThat(ts.controller.deployed.keySet(), hasItems("foo.war", "failure.ear"));
    }

    @Test
    public void testArchivePatterns() {
        Pattern pattern = FileSystemDeploymentService.ARCHIVE_PATTERN;

        assertTrue(pattern.matcher("x.war").matches());
        assertTrue(pattern.matcher("x.War").matches());
        assertTrue(pattern.matcher("x.WAr").matches());
        assertTrue(pattern.matcher("x.WAR").matches());
        assertTrue(pattern.matcher("x.jar").matches());
        assertTrue(pattern.matcher("x.Jar").matches());
        assertTrue(pattern.matcher("x.sar").matches());
        assertTrue(pattern.matcher("x.Sar").matches());
        assertTrue(pattern.matcher("x.ear").matches());
        assertTrue(pattern.matcher("x.Ear").matches());
        assertTrue(pattern.matcher("x.rar").matches());
        assertTrue(pattern.matcher("x.Rar").matches());
        assertTrue(pattern.matcher("x.wab").matches());
        assertTrue(pattern.matcher("x.WaB").matches());
        assertTrue(pattern.matcher("x.esa").matches());
        assertTrue(pattern.matcher("x.ESA").matches());

    }

    /**
     * Test that a scanner does not interfere with a deployment it does not own. Basic case with
     * a persistent deployment but no owner; i.e. a typical deployment added by the user via CLI or console.
     */
    @Test
    public void testIgnoreExternalPersistentDeployment() throws Exception {
        testIgnoreExternalDeployment(null);
    }

    /**
     * Test that a scanner does not interfere with a deployment it does not own. WFCORE-632 case. where external deployment
     * is not persistent, but is not owned by a scanner.
     */
    @Test
    public void testIgnoreExternalNonPersistentDeployment() throws Exception {
        testIgnoreExternalDeployment(new ExternalDeployment(null, false));
    }

    /**
     * Test that a scanner does not interfere with a deployment it does not own. WFCORE-65 case. where external deployment
     * is owned by a different scanner.
     */
    @Test
    public void testIgnoreExternalScannerDeployment() throws Exception {
        PathAddress externalScanner = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, DeploymentScannerExtension.SUBSYSTEM_NAME),
                PathElement.pathElement(DeploymentScannerExtension.SCANNERS_PATH.getKey(), "other"));

        testIgnoreExternalDeployment(new ExternalDeployment(externalScanner, false));
    }

    /**
     * Checks that deployments are redeployed after reboot (as in {@link #testArchiveRedeployedAfterReboot()}),
     * even if deployment dir is not available during scanner start.
     */
    @Test
    public void testUnreadableDeploymentDirDuringStart() throws Exception {
        if (!tmpDir.delete()) {
            Assert.fail("Couldn't delete tmpDir");
        }

        // start scanner with non-readable deployment dir
        TesteeSet ts = createTestee();
        ts.controller.addCompositeSuccessResponse(1);

        // create deployment dir with archive deployment and .DEPLOYED marker - this deployment should be redeployed
        createFile("foo.war");
        File dodeploy = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = createFile("foo.war" + FileSystemDeploymentService.DEPLOYED);

        // run scan
        ts.testee.scan();
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertTrue(ts.controller.deployed.containsKey("foo.war"));
    }

    /**
     * Checks temporary inaccessible deployment dir
     */
    @Test
    public void testUnreadableDeploymentDirDuringScan() throws Exception {
        createFile("foo.war");
        createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);

        TesteeSet ts = createTestee();
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertEquals(1, ts.controller.deployed.size());
        byte[] deploymentHash = ts.controller.deployed.get("foo.war");

        // make the deployment dir temporary inaccessible and run a scan
        File movedDir = new File(tmpDir.getAbsolutePath() + ".moved");
        Files.move(tmpDir.toPath(), movedDir.toPath());
        ts.testee.scan(); // scan should not fail
        assertEquals(1, ts.controller.deployed.size());
        assertEquals(deploymentHash, ts.controller.deployed.get("foo.war")); // shouldn't have been redeployed

        // make the deployment dir accessible again and run a scan
        Files.move(movedDir.toPath(), tmpDir.toPath());
        ts.testee.scan();
        assertEquals(1, ts.controller.deployed.size());
        assertEquals(deploymentHash, ts.controller.deployed.get("foo.war")); // shouldn't have been redeployed

        // update marker file timestamp and run a scan
        if (!deployed.setLastModified(System.currentTimeMillis() + 1000)) {
            Assert.fail("Couldn't update last modified flag.");
        }
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();
        assertEquals(1, ts.controller.deployed.size());
        assertNotEquals(deploymentHash, ts.controller.deployed.get("foo.war")); // should have been redeployed
    }

    private void testIgnoreExternalDeployment(ExternalDeployment externalDeployment) throws Exception {
        File war = createFile("foo.war");
        File dodeploy = createFile("foo.war" + FileSystemDeploymentService.DO_DEPLOY);
        File deployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DEPLOYED);
        File undeployed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.UNDEPLOYED);
        File failed = new File(tmpDir, "foo.war" + FileSystemDeploymentService.FAILED_DEPLOY);
        TesteeSet ts = createTestee();

        //Deploy externally loaded deployment
        byte[] bytes = randomHash();
        ts.controller.added.put("external.war", bytes);
        ts.controller.deployed.put("external.war", bytes);
        ts.controller.externallyDeployed.put("external.war", externalDeployment);

        // Scan
        ts.controller.addCompositeSuccessResponse(1);
        ts.testee.scan();

        assertTrue(war.exists());
        assertFalse(dodeploy.exists());
        assertTrue(deployed.exists());
        assertFalse(undeployed.exists());
        assertFalse(failed.exists());
        assertEquals(2, ts.controller.added.size());
        assertEquals(2, ts.controller.deployed.size());
        assertEquals(bytes, ts.controller.added.get("external.war"));
        assertEquals(bytes, ts.controller.deployed.get("external.war"));
    }

    private TesteeSet createTestee(String... existingContent) {
        return createTestee(new MockServerController(existingContent));
    }

    private TesteeSet createTestee(final DiscardTaskExecutor executorService, final String... existingContent) {
        return createTestee(new MockServerController(executorService, existingContent), executorService);
    }

    private TesteeSet createTestee(final MockServerController sc) {
        return createTestee(sc, executor);
    }

    private TesteeSet createTestee(final MockServerController sc, final ScheduledExecutorService executor) {
        return createTestee(sc, executor, sc.create());
    }

    private TesteeSet createTestee(final MockServerController sc, final ScheduledExecutorService executor, DeploymentOperations ops) {
        final FileSystemDeploymentService testee = new FileSystemDeploymentService(resourceAddress, null, tmpDir, null, sc, executor);
        testee.startScanner(ops);
        return new TesteeSet(testee, sc);
    }


    private File createFile(String fileName) throws IOException {
        return createFile(tmpDir, fileName);
    }

    private File createFile(File dir, String fileName) throws IOException {

        dir.mkdirs();
        File f = new File(dir, fileName);
        Files.write(f.toPath(), fileName.getBytes(StandardCharsets.UTF_8));
        assertTrue(f.exists());
        return f;
    }

    private File createXmlFile(String fileName, String contents) throws IOException {
        tmpDir.mkdirs();

        File f = new File(tmpDir, fileName);
        Files.write(f.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        assertTrue(f.exists());
        return f;
    }

    private File createDirectory(String name, String... children) throws IOException {
        return createDirectory(tmpDir, name, children);
    }

    private File createDirectory(File dir, String name, String... children) throws IOException {

        final File directory = new File(dir, name);
        directory.mkdirs();

        for (final String child : children) {
            createFile(directory, child);
        }

        return directory;
    }

    private static class TesteeSet {
        private final FileSystemDeploymentService testee;
        private final MockServerController controller;

        public TesteeSet(FileSystemDeploymentService testee, MockServerController sc) {
            this.testee = testee;
            this.controller = sc;
        }
    }

    private static class MockServerController implements LocalModelControllerClient, ModelControllerClientFactory, DeploymentOperations.Factory {

        private final DiscardTaskExecutor executorService;
        private final List<ModelNode> requests = new ArrayList<>(1);
        private final List<Response> responses = new ArrayList<>(1);
        private final Map<String, byte[]> added = new HashMap<>();
        private final Map<String, byte[]> deployed = new HashMap<>();
        private final Map<String, ExternalDeployment> externallyDeployed = new HashMap<>();

        @Override
        public OperationResponse executeOperation(Operation operation, OperationMessageHandler messageHandler) {
            ModelNode rawOp = operation.getOperation();
            requests.add(rawOp);
            return OperationResponse.Factory.createSimple(processOp(rawOp));
        }

        @Override
        public CompletableFuture<ModelNode> executeAsync(Operation operation, OperationMessageHandler messageHandler) {
            return executorService.submit(new Callable<>() {
                @Override
                public ModelNode call() {
                    return execute(operation);
                }
            });
        }

        @Override
        public CompletableFuture<OperationResponse> executeOperationAsync(Operation operation, OperationMessageHandler messageHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            executorService.shutdown();
        }

        @Override
        public DeploymentOperations create() {
            return new DefaultDeploymentOperations(this, executorService);
        }

        @Override
        public LocalModelControllerClient createClient(Executor executor) {
            return this;
        }

        @Override
        public LocalModelControllerClient createSuperUserClient(Executor executor, boolean forUserCalls) {
            return  this;
        }

        private static class Response {
            private final boolean ok;
            private final ModelNode rsp;
            Response(boolean ok, ModelNode rsp) {
                this.ok = ok;
                this.rsp = rsp;
            }
        }

        MockServerController(String... existingDeployments) {
            this(executor, existingDeployments);
        }

        MockServerController(DiscardTaskExecutor executorService, String... existingDeployments) {
            for (String dep : existingDeployments) {
                added.put(dep, randomHash());
                deployed.put(dep, added.get(dep));
            }
            this.executorService = executorService;
        }

        public void addCompositeSuccessResponse(int count) {
            ModelNode rsp = new ModelNode();
            rsp.get(OUTCOME).set(SUCCESS);
            ModelNode result = rsp.get(RESULT);
            for (int i = 1; i <= count; i++) {
                result.get("step-" + i, OUTCOME).set(SUCCESS);
                result.get("step-" + i, RESULT);
            }

            responses.add(new Response(true, rsp));
        }

        public void addCompositeFailureResponse(int count, int failureStep) {

            if (count < failureStep) {
                throw new IllegalArgumentException("failureStep must be > count");
            }

            ModelNode rsp = new ModelNode();
            rsp.get(OUTCOME).set(FAILED);
            ModelNode result = rsp.get(RESULT);
            for (int i = 1; i <= count; i++) {
                String step = "step-" + i;
                if (i < failureStep) {
                    result.get(step, OUTCOME).set(FAILED);
                    result.get(step, RESULT);
                    result.get(step, ROLLED_BACK).set(true);
                } else if (i == failureStep) {
                    result.get(step, OUTCOME).set(FAILED);
                    result.get(step, FAILURE_DESCRIPTION).set(new ModelNode().set("badness happened"));
                    result.get(step, ROLLED_BACK).set(true);
                } else {
                    result.get(step, OUTCOME).set(CANCELLED);
                }
            }
            rsp.get(FAILURE_DESCRIPTION).set(new ModelNode().set("badness happened"));
            rsp.get(ROLLED_BACK).set(true);

            responses.add(new Response(true, rsp));
        }

        public void addCompositeFailureResultResponse(int count, int failureStep) {

            if (count < failureStep) {
                throw new IllegalArgumentException("failureStep must be > count");
            }

            ModelNode rsp = new ModelNode();
            rsp.get(OUTCOME).set(SUCCESS);
            ModelNode result = rsp.get(RESULT);
            ModelNode failedStep = result.get("step-" + failureStep);
            failedStep.get(OUTCOME).set(SUCCESS);
            ModelNode stepResult = failedStep.get(RESULT);
            for (int i = 1; i <= count; i++) {
                String step = "step-" + i;
                if (i < failureStep) {
                    stepResult.get(step, OUTCOME).set(SUCCESS);
                    stepResult.get(step, RESULT);
                    stepResult.get(step, ROLLED_BACK).set(true);
                } else if (i == failureStep) {
                    stepResult.get(step, OUTCOME).set(FAILED);
                    stepResult.get(step, FAILURE_DESCRIPTION).set(new ModelNode().set("true failed step"));
                    stepResult.get(step, ROLLED_BACK).set(true);
                } else {
                    stepResult.get(step, OUTCOME).set(CANCELLED);
                }
            }
            rsp.get(FAILURE_DESCRIPTION).set(new ModelNode().set("badness happened"));
            rsp.get(ROLLED_BACK).set(true);

            responses.add(new Response(true, rsp));
        }

        public void addPartialCompositeFailureResultResponse(int count, int failureStep) {

            if (count < failureStep) {
                throw new IllegalArgumentException("failureStep must be > count");
            }

            ModelNode rsp = new ModelNode();
            rsp.get(OUTCOME).set(SUCCESS);
            ModelNode result = rsp.get(RESULT);
            for (int i = 1; i <= count; i++) {
                String step = "step-" + i;
                if (i < failureStep) {
                    result.get(step, OUTCOME).set(SUCCESS);
                    result.get(step, RESULT);
                    result.get(step, RESULT, "step-1", OUTCOME).set(SUCCESS);
                    result.get(step, RESULT, "step-1", RESULT);
                    result.get(step, RESULT, "step-2", OUTCOME).set(SUCCESS);
                    result.get(step, RESULT, "step-2", RESULT);
                } else if (i == failureStep) {
                    result.get(step, OUTCOME).set(SUCCESS);
                    result.get(step, RESULT);
                    result.get(step, RESULT, "step-1", OUTCOME).set(SUCCESS);
                    result.get(step, RESULT, "step-1", RESULT);
                    result.get(step, RESULT, "step-2", OUTCOME).set(FAILED);
                    result.get(step, RESULT, "step-2", FAILURE_DESCRIPTION).set(new ModelNode().set("badness happened"));
                } else {
                    result.get(step, OUTCOME).set(CANCELLED);
                }
            }

            responses.add(new Response(true, rsp));
        }

        private ModelNode getDeploymentNamesResponse() {
            ModelNode content = new ModelNode();
            content.get(OUTCOME).set(SUCCESS);
            ModelNode result = content.get(RESULT);
            result.setEmptyObject();
            for (String deployment : added.keySet()) {
                result.get(deployment, ENABLED).set(deployed.containsKey(deployment));
                if (externallyDeployed.containsKey(deployment)) {
                    ExternalDeployment externalDeployment = externallyDeployed.get(deployment);
                    if (externalDeployment != null) {
                        // Report what a different owner (e.g. different scanner) would configure
                        result.get(deployment, PERSISTENT).set(externalDeployment.persistent);
                        ModelNode ownerNode = result.get(deployment, OWNER);
                        if (externalDeployment.ownerAddress != null) {
                            ownerNode.set(externalDeployment.ownerAddress.toModelNode());
                        } // else leave undefined
                    } else {
                        // Report what a non-scanner deployment would configure
                        result.get(deployment, PERSISTENT).set(true);
                        result.get(deployment, OWNER).set(new ModelNode());
                    }
                } else {
                    // Report what our scanner would configure
                    result.get(deployment, PERSISTENT).set(false);
                    result.get(deployment, OWNER).set(resourceAddress.toModelNode().asObject());
                }
            }
            return content;
        }

        private ModelNode processOp(ModelNode op) {

            String opName = op.require(OP).asString();
            if (READ_CHILDREN_RESOURCES_OPERATION.equals(opName)) {
                return getDeploymentNamesResponse();
            } else if (COMPOSITE.equals(opName)) {
                for (ModelNode child : op.require(STEPS).asList()) {
                    opName = child.require(OP).asString();
                    if (COMPOSITE.equals(opName)) {
                        return processOp(child);
                    }

                    if (responses.isEmpty()) {
                        Assert.fail("unexpected request " + op);
                        return null; // unreachable
                    }

                    if (!responses.get(0).ok) {
                        // don't change state for a failed response
                        continue;
                    }

                    PathAddress address = PathAddress.pathAddress(child.require(OP_ADDR));
                    if (ADD.equals(opName)) {
                        // Since AS7-431 the content is no longer managed
                        //added.put(address.getLastElement().getValue(), child.require(CONTENT).require(0).require(HASH).asBytes());
                        added.put(address.getLastElement().getValue(), randomHash());
                    } else if (REMOVE.equals(opName)) {
                        added.remove(address.getLastElement().getValue());
                    } else if (DEPLOY.equals(opName)) {
                        String name = address.getLastElement().getValue();
                        deployed.put(name, added.get(name));
                    } else if (UNDEPLOY.equals(opName)) {
                        deployed.remove(address.getLastElement().getValue());
                    } else if (REDEPLOY.equals(opName)) {
                        byte[] newHash = randomHash();
                        added.put(address.getLastElement().getValue(), newHash);
                        deployed.put(address.getLastElement().getValue(), newHash);
                    } else if (FULL_REPLACE_DEPLOYMENT.equals(opName)) {
                        String name = child.require(NAME).asString();
                        // Since AS7-431 the content is no longer managed
                        //byte[] hash = child.require(CONTENT).require(0).require(HASH).asBytes();
                        final byte[] hash = randomHash();
                        added.put(name, hash);
                        deployed.put(name, hash);
                    } else {
                        throw new IllegalArgumentException("unexpected step " + opName);
                    }
                }
                return responses.remove(0).rsp;
            } else {
                throw new IllegalArgumentException("unexpected operation " + opName);
            }
        }

    }

    private static class DiscardTaskExecutor extends ScheduledThreadPoolExecutor {

        private final List<Runnable> tasks = new ArrayList<>();
        private final boolean allowRejection;
        private DiscardTaskExecutor() {
            this(false);
        }

        private DiscardTaskExecutor(boolean mayShutdown) {
            super(0);
            this.allowRejection = mayShutdown;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            tasks.add(command);
            return null;
        }

        @Override
        public <T> CompletableFuture<T> submit(Callable<T> tCallable) {
            if (allowRejection && (isShutdown() || isTerminating())) {
                throw new RejectedExecutionException("DiscardTaskExecutor has shutdown we can't run " + tCallable);
            }
            return new CallOnGetFuture<>(tCallable);
        }

        void clear() {
            tasks.clear();
        }
    }

    private static class CallOnGetFuture<T> extends CompletableFuture<T> {
        final Callable<T> callable;

        private CallOnGetFuture(Callable<T> callable) {
            this.callable = callable;
        }

        @Override
        public boolean cancel(boolean b) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            try {
                complete(callable.call());
            } catch (Exception e) {
                completeExceptionally(e);
            }
            return super.get();
        }

        @Override
        public T get(long l, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            return get();
        }
    }

    private static class TimeOutFuture<T> extends CallOnGetFuture<T> {
        final long expectedTimeout;

        private TimeOutFuture(long expectedTimeout, Callable<T> callable) {
            super(callable);
            this.expectedTimeout = expectedTimeout;
        }

        @Override
        public T get(long l, TimeUnit timeUnit) throws TimeoutException {
            assertEquals("Should use the configured timeout", expectedTimeout, l);
            throw new TimeoutException();
        }
    }

    private static class WaitingFuture<T> extends CallOnGetFuture<T> {
        private final long duration;
        private WaitingFuture(long duration, Callable<T> callable) {
            super(callable);
            this.duration = duration;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            Thread.sleep(duration);
            return super.get();
        }
    }

    private static class BlockingDeploymentOperations implements DeploymentOperations {
        private volatile boolean ready = false;
        private final DeploymentOperations delegate;

        BlockingDeploymentOperations(final DeploymentOperations delegate) {
            this.delegate = delegate;
        }

        @Override
        public Future<ModelNode> deploy(final ModelNode operation, ExecutorService executorService) {
            ready = true;
            return delegate.deploy(operation, null);
        }

        @Override
        public Map<String, Boolean> getDeploymentsStatus() {
            return delegate.getDeploymentsStatus();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public Set<String> getUnrelatedDeployments(ModelNode owner) {
            return delegate.getUnrelatedDeployments(owner);
        }

    }

    private static class ExternalDeployment {
        private final PathAddress ownerAddress;
        private final boolean persistent;

        private ExternalDeployment(PathAddress ownerAddress, boolean persistent) {
            this.ownerAddress = ownerAddress;
            this.persistent = persistent;
        }
    }

    private static byte[] randomHash() {
        final byte[] hash = new byte[20];
        random.nextBytes(hash);
        return hash;
    }
}
