/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2017  Khartec Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import _ from "lodash";
import { initialiseData, invokeFunction } from "../../../common";

import template from "./dynamic-section-navigation.html";


const bindings = {
    sections: '<',
    onSelect: '<',
    offset: '@?'
};


const initialState = {
    offset: 250,
    stickyVisible: false,
    onSelect: (w) => console.log('default on-select handler for dynamic-section-navigation: ', w),
};


function controller($scope,
                    $window) {
    const vm = initialiseData(this, initialState);

    const scrollListener = () => {
        $scope.$applyAsync(() => {
            vm.stickyVisible = $window.pageYOffset > vm.offset;
        });
    };

    vm.$onInit = () => {
        angular
            .element($window)
            .on("scroll", _.throttle(scrollListener, 100));
    };

    vm.$onDestroy = () => {
        angular
            .element($window)
            .off("scroll", scrollListener);
    };


    // -- INTERACT --

    vm.scrollAndSelectSection = (section) => {
        invokeFunction(vm.onSelect, section);
        $window.scrollTo(0, vm.offset);
    };

}


controller.$inject = [
    '$scope',
    '$window'
];


const component = {
    template,
    bindings,
    controller
};


export default {
    component,
    id: 'waltzDynamicSectionNavigation'
};
