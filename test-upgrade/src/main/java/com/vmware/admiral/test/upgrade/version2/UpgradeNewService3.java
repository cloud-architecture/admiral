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

package com.vmware.admiral.test.upgrade.version2;

import java.util.Map;
import java.util.Set;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.serialization.ThreadLocalVersionHolder;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.test.upgrade.common.UpgradeUtil;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService3;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents the base service {@link UpgradeOldService3} with new field types.
 */
public class UpgradeNewService3 extends StatefulService {

    public static final String FACTORY_LINK = UpgradeUtil.UPGRADE_SERVICE3_FACTORY_LINK;

    public static class UpgradeNewService3State extends ServiceDocument {

        public static final String KIND = UpgradeUtil.UPGRADE_SERVICE3_STATE_KIND;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field1;

        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String field2;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long field3;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Set<String> field4;

        // @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        // @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
        // PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        // public Map<String, String> field5;

        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Map<String, String> field6;
    }

    public UpgradeNewService3() {
        super(UpgradeNewService3State.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    static {
        UpgradeNewService3StateConverter.INSTANCE.init();
    }

    @Override
    public void handleRequest(Operation request) {
        ThreadLocalVersionHolder.setVersion(request);
        try {
            super.handleRequest(request);
        } finally {
            ThreadLocalVersionHolder.clearVersion();
        }
    }

    @Override
    public void handleStart(Operation post) {
        UpgradeNewService3State body = post.getBody(UpgradeNewService3State.class);
        AssertUtil.assertNotNull(body, "body");
        // validate based on annotations
        Utils.validateState(getStateDescription(), body);
        super.handleStart(post);
    }

}
