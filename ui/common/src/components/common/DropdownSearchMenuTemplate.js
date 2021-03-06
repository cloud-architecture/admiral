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

module.exports = function(componentOptions) {
  let searchHtml = '';
  if (!componentOptions.searchDisabled) {
    searchHtml = `<div class="dropdown-search">
          <div class="search-input">
            <input type="text" placeholder="${componentOptions.searchPlaceholder}">
            <i class="fa fa-spinner fa-spin loader-inline form-control-feedback"></i>
            <i class="fa fa-search search-hint form-control-feedback"></i>
          </div>
        </div>`;
  }

  return `
    <div class="dropdown dropdown-select">
      <button class="dropdown-toggle" data-toggle="dropdown" aria-expanded="true">
        <div class="dropdown-title placeholder">${componentOptions.title}</div>
        <div class="loading hide">
          <div class="spinner spinner-inline"></div>
        </div>
      </button>
      <div class="dropdown-menu">
        ${searchHtml}
        <ul class="dropdown-options" role="menu" aria-labelledby="menu1">
        <!-- Runtime options will appear here -->
        </ul>
        <div class="divider hide" role="presentation"></div>
        <ul class="dropdown-manage hide">
        <!-- Runtime menu options will appear here -->
        </ul>
      </div>
    </div>`;
}
