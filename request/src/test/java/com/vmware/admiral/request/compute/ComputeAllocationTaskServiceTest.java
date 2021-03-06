/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.compute.ComputeConstants.CUSTOM_PROP_PROFILE_LINK_NAME;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState.SubStage;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService.ComputeProvisionTaskState;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService.ExtensibilityCallbackResponse;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.EventTopicDeclarator;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.admiral.service.common.EventTopicService.EventTopicState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.test.TestContext;

public class ComputeAllocationTaskServiceTest extends ComputeRequestBaseTest {

    @Override
    protected ResourceType placementResourceType() {
        return ResourceType.COMPUTE_TYPE;
    }

    @Test
    public void testAllocationTaskServiceLifeCycle() throws Throwable {
        ComputeDescription computeDescription = createVMComputeDescription(false);

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.iterator().next());
        assertNotNull(computeState.id);

        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(resourcePool.documentSelfLink, computeState.resourcePoolLink);
        // assertEquals(vmHostCompute.documentSelfLink, computeState.parentLink);
    }

    @Test
    public void testAllocationTaskServiceLifeCycleWithTwoProfilesByInstanceType() throws Throwable {
        ComputeDescription computeDescription = createVMComputeDescription(false);

        ProfileStateExpanded p1 = createProfileWithInstanceType(
                "small", "t2.micro", "coreos", "ami-234355");
        @SuppressWarnings("unused")
        ProfileStateExpanded p2 =
                createProfileWithInstanceType("large", "t2.large", "coreos", "ami-234355");

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.iterator().next());
        assertNotNull(computeState.id);

        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(resourcePool.documentSelfLink, computeState.resourcePoolLink);
        assertNotNull(computeState.customProperties);
        String profileLink = computeState.customProperties.get(CUSTOM_PROP_PROFILE_LINK_NAME);
        assertNotNull(profileLink);
        assertEquals(p1.documentSelfLink, profileLink);
    }

    @Test
    public void testAllocationTaskServiceLifeCycleWithTwoProfilesByImage() throws Throwable {
        ComputeDescription computeDescription = createVMComputeDescription(false);

        @SuppressWarnings("unused")
        ProfileStateExpanded p1 = createProfileWithInstanceType(
                "small", "t2.micro", "linux", "ami-234355");
        ProfileStateExpanded p2 =
                createProfileWithInstanceType("small", "t2.large", "coreos", "ami-234355");

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.iterator().next());
        assertNotNull(computeState.id);

        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(resourcePool.documentSelfLink, computeState.resourcePoolLink);
        assertNotNull(computeState.customProperties);
        String profileLink = computeState.customProperties.get(CUSTOM_PROP_PROFILE_LINK_NAME);
        assertNotNull(profileLink);
        assertEquals(profileLink, p2.documentSelfLink);
    }

    @Test
    public void testComputeAllocationWithFollowingProvisioningRequest() throws Throwable {
        host.log(">>>>>>Start: testComputeAllocationWithFollowingProvisioningRequest <<<<< ");
        ComputeDescription computeDescription = createVMComputeDescription(false);

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.iterator().next());
        assertTrue(computeState.name.startsWith(TEST_VM_NAME));
        assertNotNull(computeState.id);
        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(allocationTask.tenantLinks, computeState.tenantLinks);

        // make sure the host is not update with the new container.
        assertFalse("should not be provisioned container: " + computeState.documentSelfLink,
                MockDockerAdapterService.isContainerProvisioned(computeState.documentSelfLink));

        ComputeProvisionTaskState provisionTask = createComputeProvisionTask(
                allocationTask.resourceLinks);

        // Request provisioning after allocation:
        provisionTask = provision(provisionTask);

        // verify container state is provisioned and patched:
        computeState =
                getDocument(ComputeState.class, provisionTask.resourceLinks.iterator().next());
        assertNotNull(computeState);

        assertNotNull(computeState.id);
        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        // assertEquals(vmHostCompute.documentSelfLink, computeState.parentLink);
    }

    @Test
    public void testVsphereAllocationWithFollowingProvisioningRequest() throws Throwable {
        host.log(">>>>>>Start: testVsphereAllocationWithFollowingProvisioningRequest <<<<< ");
        ComputeDescription computeDescription = createVsphereComputeDescription(false,
                null);

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.iterator().next());
        assertTrue(computeState.name.startsWith(TEST_VM_NAME));
        assertNotNull(computeState.id);
        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(allocationTask.tenantLinks, computeState.tenantLinks);

        // make sure the host is not update with the new container.
        assertFalse("should not be provisioned container: " + computeState.documentSelfLink,
                MockDockerAdapterService.isContainerProvisioned(computeState.documentSelfLink));

        ComputeProvisionTaskState provisionTask = createComputeProvisionTask(
                allocationTask.resourceLinks);

        // Request provisioning after allocation:
        provisionTask = provision(provisionTask);

        // verify container state is provisioned and patched:
        computeState =
                getDocument(ComputeState.class, provisionTask.resourceLinks.iterator().next());
        assertNotNull(computeState);

        assertNotNull(computeState.id);
        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
    }

    @SuppressWarnings("static-access")
    @Test
    public void testContainerAllocationSubscriptionSubStages() throws Throwable {

        ComputeDescription computeDescription = createVMComputeDescription(false);
        ComputeAllocationTaskState allocationTask = createComputeAllocationTask
                (computeDescription.documentSelfLink, 1, true);
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put("customPropB", "valueB");
        allocationTask = allocate(allocationTask);

        assertNotNull(allocationTask);
        Set<SubStage> subscriptionSubStages = allocationTask.taskSubStage
                .SUBSCRIPTION_SUB_STAGES;

        assertNotNull(subscriptionSubStages);
        assertEquals(1, subscriptionSubStages.size());
        assertTrue(subscriptionSubStages
                .contains(allocationTask.taskSubStage.START_COMPUTE_ALLOCATION));
    }

    @Test
    public void testNotificationPayload() {
        ComputeProvisionTaskService service = new ComputeProvisionTaskService();
        ComputeProvisionTaskService.ExtensibilityCallbackResponse payload =
                (ExtensibilityCallbackResponse) service.notificationPayload(null);

        List<Field> fields = Arrays.asList(payload.getClass().getFields());

        assertNotNull(fields);

        List<Field> subnetName = fields.stream()
                .filter(f -> f.getName().equals("subnetName") || f.getName().equals("subnetCIDR")
                        || f.getName().equals("resourceNames") || f.getName().equals("addresses"))
                .collect(Collectors.toList());

        assertNotNull(subnetName);
        assertEquals(4, subnetName.size());
    }

    @Test
    public void testComputeProvisionTaskServiceEventTopics() throws Throwable {

        EventTopicDeclarator service = new ComputeProvisionTaskService();
        service.registerEventTopics(host);

        //Get PreProvision EventTopic
        EventTopicState preProvision = getDocument(EventTopicState.class,
                String.format("%s/%s", EventTopicService.FACTORY_LINK, ComputeProvisionTaskService
                        .COMPUTE_PROVISION_TOPIC_TASK_SELF_LINK));

        assertNotNull(preProvision);
        assertEquals(ComputeProvisionTaskService.COMPUTE_PROVISION_TOPIC_NAME, preProvision.name);
        assertEquals(ComputeProvisionTaskService.COMPUTE_PROVISION_TOPIC_TASK_DESCRIPTION, preProvision.description);
        assertEquals(ComputeProvisionTaskService.COMPUTE_PROVISION_TOPIC_ID, preProvision.id);
        assertEquals(Boolean.TRUE, preProvision.blockable);

        assertNotNull(preProvision.topicTaskInfo);
        assertEquals(TaskStage.STARTED.name(), preProvision
                .topicTaskInfo.stage);
        assertEquals(ComputeProvisionTaskState.SubStage.CUSTOMIZING_COMPUTE.name(), preProvision
                .topicTaskInfo.substage);

        //Get PostProvision EventTopic
        EventTopicState postProvision = getDocument(EventTopicState.class,
                String.format("%s/%s", EventTopicService.FACTORY_LINK, ComputeProvisionTaskService
                        .COMPUTE_POST_PROVISION_TOPIC_TASK_SELF_LINK));

        assertNotNull(postProvision);
        assertEquals(ComputeProvisionTaskService.COMPUTE_POST_PROVISION_TOPIC_NAME, postProvision.name);
        assertEquals( ComputeProvisionTaskService
                .COMPUTE_POST_PROVISION_TOPIC_TASK_DESCRIPTION, postProvision.description);
        assertEquals(ComputeProvisionTaskService.COMPUTE_POST_PROVISION_TOPIC_ID, postProvision.id);
        assertEquals(Boolean.TRUE, postProvision.blockable);

        assertNotNull(postProvision.topicTaskInfo);
        assertEquals(TaskStage.FINISHED.name(), postProvision.topicTaskInfo.stage);
        assertEquals(ComputeProvisionTaskState.SubStage.COMPLETED.name(), postProvision
                .topicTaskInfo.substage);
    }

    @Test
    public void testPatchNicDescriptionOperation() throws Throwable {

        String ipAddress = "10.152.8.10";

        NetworkInterfaceDescription netDsc = new NetworkInterfaceDescription();
        netDsc.tenantLinks = computeGroupPlacementState.tenantLinks;
        netDsc.assignment = IpAssignment.DYNAMIC;

        NetworkInterfaceDescription description = doPost(netDsc,
                NetworkInterfaceDescriptionService.FACTORY_LINK);

        TestContext context = new TestContext(1, Duration.ofMinutes(1));

        ComputeProvisionTaskService service = new ComputeProvisionTaskService();
        service.setHost(host);

        Operation operation = service
                .patchNicDescriptionOperation(ipAddress, description.documentSelfLink);

        //Overload completion handler
        operation.setCompletion((o, e) -> {
            if (e != null) {
                context.failIteration(e);
                return;
            }
            NetworkInterfaceDescription body = o.getBody(NetworkInterfaceDescription.class);
            assertEquals(IpAssignment.STATIC, body.assignment);
            assertEquals(ipAddress, body.address);
            context.completeIteration();
        }).sendWith(host);

        context.await();
    }

    @Test
    public void testPatchNicStateOperation() throws Throwable {

        String ipAddress = "10.152.8.10";

        NetworkInterfaceState netState = new NetworkInterfaceState();
        netState.tenantLinks = computeGroupPlacementState.tenantLinks;
        netState.networkLink = "test-network";
        netState.subnetLink = "test-subnet";

        NetworkInterfaceState state = doPost(netState,
                NetworkInterfaceService.FACTORY_LINK);

        TestContext context = new TestContext(1, Duration.ofMinutes(1));

        ComputeProvisionTaskService service = new ComputeProvisionTaskService();
        service.setHost(host);

        SubnetState subnet = new SubnetState();
        subnet.documentSelfLink = "subnet-link";

        Operation operation = service
                .patchNicStateOperation(subnet, ipAddress, state.documentSelfLink);

        //Overload completion handler
        operation.setCompletion((o, e) -> {
            if (e != null) {
                context.failIteration(e);
                return;
            }
            NetworkInterfaceState body = o.getBody(NetworkInterfaceState.class);
            assertEquals(ipAddress, body.address);
            assertEquals(subnet.documentSelfLink, body.subnetLink);
            context.completeIteration();
        });

        operation.sendWith(host);
        context.await();
    }

    @Test
    public void testEnhanceNotificationPayload() {
        ComputeProvisionTaskService service = new ComputeProvisionTaskService();
        service.setHost(host);

        ComputeProvisionTaskState state = new ComputeProvisionTaskState();
        state.resourceLinks = Collections.singleton(computeHost.documentSelfLink);

        ComputeProvisionTaskService.ExtensibilityCallbackResponse payload = (ExtensibilityCallbackResponse) service
                .notificationPayload(state);

        TestContext context = new TestContext(1, Duration.ofMinutes(1));

        service.enhanceNotificationPayload(state, Arrays.asList(computeHost), payload).whenComplete((r,
                err) ->
                context.completeIteration());
        context.await();
    }

    @Test
    public void testEnhanceExtensibilityResponse() throws Throwable {

        /**
         * Initially the placement is:
         *  H(1)    H(2)
         *   |       |
         *   c1      c2
         *
         * After patch from client:
         *  H(2)    H(1)
         *   |       |
         *   c1      c2
         *
         */

        createVmHostCompute(true);

        ComputeDescription computeDescription = createVMComputeDescription(false);

        ComputeAllocationTaskService service = new ComputeAllocationTaskService();
        service.setHost(host);

        ComputeAllocationTaskState state = createComputeAllocationTask(computeDescription
                .documentSelfLink, 2, true);

        state = doPost(state,
                ComputeAllocationTaskService.FACTORY_LINK);

        final String selfLink = state.documentSelfLink;
        assertNotNull(selfLink);

        state = allocate(state);

        assertNotNull(state);
        assertEquals(2, state.resourceLinks.size());

        ComputeAllocationTaskService.ExtensibilityCallbackResponse payload =
                (ComputeAllocationTaskService.ExtensibilityCallbackResponse) service
                        .notificationPayload(state);

        List<HostSelection> beforeExtensibility = new ArrayList<>(
                state.selectedComputePlacementHosts);

        payload.hostSelections = beforeExtensibility.stream()
                .map(hs -> hs.name)
                .collect(Collectors.toList());

        Collections.reverse(payload.hostSelections);

        TestContext context = new TestContext(1, Duration.ofMinutes(1));

        service.enhanceExtensibilityResponse(state, payload).whenComplete((r, err) -> {
            try {
                ComputeAllocationTaskState document = getDocument(ComputeAllocationTaskState.class,
                        selfLink);

                assertNotNull(document);

                List<HostSelection> patchedHosts = new ArrayList<HostSelection>(document
                        .selectedComputePlacementHosts);

                assertNotNull(patchedHosts);

                assertNotEquals(beforeExtensibility, patchedHosts);

                //Task is in complete state and won't remove initial host selections, just add newfo
                //ones to the existing which have been assigned when document has been initialized.
                if (patchedHosts.size() == 4) {
                    //Assert that new newly added host selections are in proper order (reversed
                    // of original)
                    assertEquals(beforeExtensibility.get(1).name, patchedHosts.get(2).name);
                    assertEquals(beforeExtensibility.get(0).name, patchedHosts.get(3).name);
                } else {
                    context.failIteration(new Throwable("Expected 4 host selections."));
                }
            } catch (Throwable throwable) {
                context.failIteration(throwable);
            }
            context.completeIteration();
        });
        context.await();
    }

    @Test
    public void testEmptyHostSelections() throws Throwable {

        createVmHostCompute(true);

        ComputeDescription computeDescription = createVMComputeDescription(false);

        ComputeAllocationTaskService service = new ComputeAllocationTaskService();
        service.setHost(host);

        ComputeAllocationTaskState state = createComputeAllocationTask(computeDescription
                .documentSelfLink, 2, true);

        state = doPost(state,
                ComputeAllocationTaskService.FACTORY_LINK);

        final String selfLink = state.documentSelfLink;
        assertNotNull(selfLink);

        state = allocate(state);

        assertNotNull(state);
        assertEquals(2, state.resourceLinks.size());

        ComputeAllocationTaskService.ExtensibilityCallbackResponse payload =
                (ComputeAllocationTaskService.ExtensibilityCallbackResponse) service
                        .notificationPayload(state);

        List<HostSelection> beforeExtensibility = new ArrayList<>(
                state.selectedComputePlacementHosts);

        payload.hostSelections = null;

        TestContext context = new TestContext(1, Duration.ofMinutes(1));

        service.enhanceExtensibilityResponse(state, payload).whenComplete((r, err) -> {
            try {
                ComputeAllocationTaskState document = getDocument(ComputeAllocationTaskState.class,
                        selfLink);

                assertNotNull(document);

                List<HostSelection> patchedHosts = new ArrayList<HostSelection>(document
                        .selectedComputePlacementHosts);

                assertNotNull(patchedHosts);

                assertEquals(beforeExtensibility.get(0).name, patchedHosts.get(0).name);
                assertEquals(beforeExtensibility.get(1).name, patchedHosts.get(1).name);
                context.completeIteration();
            } catch (Throwable throwable) {
                context.failIteration(throwable);
            }

        });
        context.await();
    }

    @Test
    public void testRetrieveSubnetwork() throws Throwable {

        ComputeProvisionTaskService service = new ComputeProvisionTaskService();
        service.setHost(host);

        //Create Subnet
        SubnetState subnet = new SubnetState();
        subnet.name = "subnetName";
        subnet.networkLink = "networkLink";
        subnet.endpointLink = "endpointLink";
        subnet.gatewayAddress = "127.0.0.1";
        subnet.subnetCIDR = "10.125.54.0/24";

        doPost(subnet, SubnetService.FACTORY_LINK);

        ComputeProvisionTaskState state = new ComputeProvisionTaskState();
        state.resourceLinks = Collections.singleton(computeHost.documentSelfLink);

        ComputeProvisionTaskService.ExtensibilityCallbackResponse payload = (ExtensibilityCallbackResponse) service
                .notificationPayload(state);
        payload.subnetName = "subnetName";
        payload.addresses = Collections.singleton("10.152.24.52");

        TestContext context = new TestContext(1, Duration.ofMinutes(1));

        service.retrieveSubnetwork(state, payload, () -> {
            context.completeIteration();
        });

        context.await();
    }

    @Test
    public void testPatchCustomProperties() throws Throwable {
        ComputeProvisionTaskService service = new ComputeProvisionTaskService();
        service.setHost(host);

        String prop1 = "prop1";
        String value1 = "value1";

        String prop2 = "prop2";
        String value2 = "value2";

        ComputeProvisionTaskState state = new ComputeProvisionTaskState();
        state.resourceLinks = Collections.singleton(computeHost.documentSelfLink);

        ComputeProvisionTaskService.ExtensibilityCallbackResponse payload = (ExtensibilityCallbackResponse) service
                .notificationPayload(state);
        payload.customProperties = new HashMap<>();
        payload.customProperties.put(prop1, value1);
        payload.customProperties.put(prop2, value2);

        TestContext context = new TestContext(1, Duration.ofMinutes(1));
        service.patchCustomPropertiesFromExtensibilityResponse(state, payload).whenComplete((r,
                err) -> {
            try {
                ComputeState document = getDocument(ComputeState.class,
                        computeHost.documentSelfLink);
                assertTrue(document.customProperties.containsKey(prop1));
                assertEquals(document.customProperties.get(prop1), value1);
                assertTrue(document.customProperties.containsKey(prop2));
                assertEquals(document.customProperties.get(prop2), value2);
            } catch (Throwable throwable) {
                context.failIteration(throwable);
            }

            context.completeIteration();
        });
        context.await();
    }

    @Test
    public void testPatchTags() throws Throwable {
        ComputeProvisionTaskService service = new ComputeProvisionTaskService();
        service.setHost(host);

        String prop1 = "tag1";
        String value1 = "value1";

        String prop2 = "tag2";
        String value2 = "value2";

        ComputeProvisionTaskState state = new ComputeProvisionTaskState();
        state.resourceLinks = Collections.singleton(computeHost.documentSelfLink);

        ComputeProvisionTaskService.ExtensibilityCallbackResponse payload = (ExtensibilityCallbackResponse) service
                .notificationPayload(state);
        payload.tags = new HashMap<>();
        payload.tags.put(prop1, value1);
        payload.tags.put(prop2, value2);

        TestContext context = new TestContext(1, Duration.ofMinutes(1));
        service.patchTagsFromExtensibilityResponse(state, payload).whenComplete((r,
                err) -> {
            try {
                ComputeState document = getDocument(ComputeState.class,
                        computeHost.documentSelfLink);

                assertTrue(document.tagLinks != null);
                assertEquals(2, document.tagLinks.size());

                List<TagState> tags = document.tagLinks.stream()
                        .map(link -> {
                            try {
                                return getDocument(TagState.class, link);
                            } catch (Throwable throwable) {
                                context.failIteration(throwable);
                                return null;
                            }
                        })
                        .sorted((t1, t2) -> t1.key.compareTo(t2.key))
                        .collect(Collectors.toList());

                assertEquals("tag1", tags.get(0).key);
                assertEquals("value1", tags.get(0).value);

                assertEquals("tag2", tags.get(1).key);
                assertEquals("value2", tags.get(1).value);

            } catch (Throwable throwable) {
                context.failIteration(throwable);
            }

            context.completeIteration();
        });
        context.await();
    }

    @Test
    public void testTrimCollection() {

        String a = "a";
        String b = "b";
        String c = "c";

        List<String> collection = new ArrayList<>();
        collection.add(a);
        collection.add(b);
        collection.add(c);

        ComputeAllocationTaskService service = new ComputeAllocationTaskService();
        service.trimCollection(collection, 1);

        assertEquals(2, collection.size());
        assertTrue(collection.contains(b));
        assertTrue(collection.contains(c));

    }

    @Test
    public void testReorderHostSelections() {
        ComputeAllocationTaskService service = new ComputeAllocationTaskService();
        service.setHost(host);

        HostSelection hs1 = new HostSelection();
        hs1.name = "hs1";

        HostSelection hs2 = new HostSelection();
        hs2.name = "hs2";

        List<HostSelection> hostSelections = Arrays.asList(new HostSelection[] { hs1, hs2 });

        ComputeAllocationTaskState state = new ComputeAllocationTaskState();
        state.selectedComputePlacementHosts = hostSelections;

        ComputeAllocationTaskService.ExtensibilityCallbackResponse payload =
                (ComputeAllocationTaskService.ExtensibilityCallbackResponse) service
                        .notificationPayload(state);
        payload.hostSelections = Arrays.asList("hs1", "hs2");

        TestContext context = new TestContext(1, Duration.ofMinutes(1));
        service.reorderHostSelections(state, payload, () -> {
            //Reordering shouldn't be invoked as both lists are the same.
            context.completeIteration();
        });
        context.await();
    }

    private ProfileStateExpanded createProfileWithInstanceType(String instanceTypeKey,
            String instanceTypeValue, String imageKey, String imageValue) throws Throwable {
        return createProfileWithInstanceType(instanceTypeKey, instanceTypeValue, imageKey, imageValue,
                null, computeGroupPlacementState);
    }

    private ComputeAllocationTaskState allocate(ComputeAllocationTaskState allocationTask)
            throws Throwable {
        allocationTask = startAllocationTask(allocationTask);
        host.log("Start allocation test: " + allocationTask.documentSelfLink);

        allocationTask = waitForTaskSuccess(allocationTask.documentSelfLink,
                ComputeAllocationTaskState.class);
        assertNotNull("ResourceLinks null for allocation: " + allocationTask.documentSelfLink,
                allocationTask.resourceLinks);
        assertEquals("Resource count not equal for: " + allocationTask.documentSelfLink,
                allocationTask.resourceCount, Long.valueOf(allocationTask.resourceLinks.size()));

        host.log("Finished allocation test: " + allocationTask.documentSelfLink);
        return allocationTask;
    }

    private ComputeProvisionTaskState provision(ComputeProvisionTaskState provisionTask)
            throws Throwable {
        provisionTask = startProvisionTask(provisionTask);
        host.log("Start allocation test: " + provisionTask.documentSelfLink);

        provisionTask = waitForTaskSuccess(provisionTask.documentSelfLink,
                ComputeProvisionTaskState.class);
        assertNotNull("ResourceLinks null for allocation: " + provisionTask.documentSelfLink,
                provisionTask.resourceLinks);

        host.log("Finished allocation test: " + provisionTask.documentSelfLink);
        return provisionTask;
    }

    private ComputeAllocationTaskState createComputeAllocationTask(String computeDescriptionLink,
            long resourceCount, boolean allocation) {
        ComputeAllocationTaskState allocationTask = new ComputeAllocationTaskState();
        allocationTask.resourceDescriptionLink = computeDescriptionLink;
        allocationTask.groupResourcePlacementLink = computeGroupPlacementState.documentSelfLink;
        allocationTask.tenantLinks = computeGroupPlacementState.tenantLinks;
        allocationTask.resourceType = "Compute";
        allocationTask.resourceCount = resourceCount;
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put("compute.docker.host", "true");
        allocationTask.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                String.valueOf(allocation));
        return allocationTask;
    }

    private ComputeProvisionTaskState createComputeProvisionTask(Set<String> resourceLinks) {
        ComputeProvisionTaskState provisionTask = new ComputeProvisionTaskState();
        provisionTask.resourceLinks = resourceLinks;
        provisionTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        provisionTask.customProperties = new HashMap<>();
        provisionTask.customProperties.put("compute.docker.host", "true");
        return provisionTask;
    }

    private ComputeAllocationTaskState startAllocationTask(
            ComputeAllocationTaskState allocationTask) throws Throwable {
        ComputeAllocationTaskState outAllocationTask = doPost(
                allocationTask, ComputeAllocationTaskService.FACTORY_LINK);
        assertNotNull(outAllocationTask);
        return outAllocationTask;
    }

    private ComputeProvisionTaskState startProvisionTask(
            ComputeProvisionTaskState provisionTask) throws Throwable {
        ComputeProvisionTaskState outprovisionTask = doPost(
                provisionTask, ComputeProvisionTaskService.FACTORY_LINK);
        assertNotNull(outprovisionTask);
        return outprovisionTask;
    }

}