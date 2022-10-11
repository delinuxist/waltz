/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific
 *
 */

import _ from "lodash";
import {initialiseData} from "../common";
import template from "./involvement-kinds-view.html";
import toasts from "../svelte-stores/toast-store";
import {displayError} from "../common/error-utils";
import InvolvementKindTable from "./svelte/involvement-kind/InvolvementKindTable.svelte";

const initialState = {
    InvolvementKindTable
};


function controller() {

    const vm = initialiseData(this, initialState);

}


controller.$inject = [
];


export default {
    template,
    controller,
    controllerAs: "ctrl",
    bindToController: true,
    scope: {}
};
