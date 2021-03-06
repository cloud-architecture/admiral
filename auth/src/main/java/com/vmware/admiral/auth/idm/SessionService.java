/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.idm;

import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.auth.util.SecurityContextUtil;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class SessionService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.AUTH_SESSION;

    private LogoutProvider provider;

    public SessionService() {
        super();
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        provider = AuthUtil.getPreferredProvider(LogoutProvider.class);
        provider.init(this);
        startPost.complete();
    }

    @Override
    public void handleGet(Operation get) {
        if (isLogoutRequest(get)) {
            provider.doLogout(get);
            SecurityContextUtil.clearSecurityContext(get);
        } else if (isSessionRequest(get)) {
            getCurrentUserSecurityContext(get)
                    .thenApply(get::setBody)
                    .whenCompleteNotify(get);
        } else {
            Operation.failServiceNotFound(get);
        }
    }

    private boolean isLogoutRequest(Operation op) {
        return ManagementUriParts.AUTH_LOGOUT.equalsIgnoreCase(op.getUri().getPath());
    }

    private boolean isSessionRequest(Operation op) {
        return SELF_LINK.equalsIgnoreCase(op.getUri().getPath());
    }

    private DeferredResult<SecurityContext> getCurrentUserSecurityContext(Operation op) {
        return SecurityContextUtil.getSecurityContext(this, op);
    }

}
