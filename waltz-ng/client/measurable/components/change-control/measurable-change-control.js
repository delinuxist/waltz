import template from "./measurable-change-control.html";
import {initialiseData} from "../../../common";
import {CORE_API} from "../../../common/services/core-api-utils";
import {toEntityRef} from "../../../common/entity-utils";
import {determineColorOfSubmitButton} from "../../../common/severity-utils";

const modes = {
    MENU: "MENU",
    OPERATION: "OPERATION",
};


const bindings = {
    measurable: "<",
    changeDomain: "<",
    onSubmitChange: "<"
};


const initialState = {
    modes: modes,
    mode: modes.MENU,
    submitDisabled: true,
    commandParams: {},
    selectedOperation: null,
    preview: null,
    command: null
};


function controller(notification,
                    serviceBroker,
                    userService) {

    const vm = initialiseData(this, initialState);

    function mkUpdCmd() {
        return {
            changeType: vm.selectedOperation.code,
            changeDomain: toEntityRef(vm.changeDomain),
            a: toEntityRef(vm.measurable),
            params: vm.commandParams,
            createdBy: vm.userName
        };
    }

    function mkPreviewCmd() {
        return {
            changeType: vm.selectedOperation.code,
            changeDomain: toEntityRef(vm.changeDomain),
            a: toEntityRef(vm.measurable),
            params: {},
            createdBy: vm.userName
        };
    }

    function calcPreview() {
        return serviceBroker
            .execute(CORE_API.TaxonomyManagementStore.preview, [ mkPreviewCmd() ])
            .then(r => {
                const preview = r.data;
                vm.preview = preview;
                const severities = _.map(preview.impacts, "severity");
                vm.submitButtonClass = determineColorOfSubmitButton(severities);
            });
    }


    function resetForm(params) {
        vm.commandParams = Object.assign({}, params);
        vm.submitDisabled = true;
    }


    const updateMenu = {
        name: "Update",
        description: `These are operations modify an existing taxonomy element.  Care must be taken 
                to prevent inadvertently altering the <em>meaning</em> of nodes.  The operations 
                will not result in data loss.`,
        options: [
            {
                name: "Name",
                code: "UPDATE_NAME",
                title: "Update name",
                description: `The name of the taxonomy item may be changed, however care should be 
                    taken to prevent inadvertently altering the <em>meaning</em> of the item`,
                icon: "edit",
                onShow: () => {
                    resetForm({ name: vm.measurable.name });
                    calcPreview();
                },
                onChange: () => {
                    vm.submitDisabled = vm.commandParams.name === vm.measurable.name;
                    vm.command = mkUpdCmd();
                }
            }, {
                name: "Description",
                code: "UPDATE_DESCRIPTION",
                icon: "edit",
                description: `The description of the taxonomy item may be changed, however care should be 
                    taken to prevent inadvertently altering the <em>meaning</em> of the item.`,
                onShow: () => {
                    resetForm({ description: vm.measurable.description });
                    calcPreview();
                },
                onChange: () => {
                    vm.submitDisabled = vm.commandParams.description === vm.measurable.description;
                    vm.command = mkUpdCmd();
                }
            }, {
                name: "Concrete",
                code: "UPDATE_CONCRETENESS",
                title: "Update Concrete Flag",
                description: `The concrete flag is used to determine whether applications may
                    use this taxonomy item to describe themselves via ratings.  Typically
                    higher level <em>grouping</em> items are non-concrete as they are not
                    specific enough to accurately describe the portfolio.`,
                icon: "edit",
                onShow: () => {
                    resetForm({ concrete: !vm.measurable.concrete });
                    vm.submitDisabled = false;
                    calcPreview();
                }
            }, {
                name: "External Id",
                code: "UPDATE_EXTERNAL_ID",
                icon: "edit",
                description: `The external identifier of the taxonomy item may be changed, however care should be 
                    taken to prevent potentially breaking downstream consumers / reporting systems that rely
                    on the identifier.`,
                onShow: () => {
                    resetForm({ externalId: vm.measurable.externalId });
                    calcPreview();
                },
                onChange: () => {
                    vm.submitDisabled = vm.commandParams.externalId === vm.measurable.externalId;
                    vm.command = mkUpdCmd();
                }
            }, {
                name: "Move",
                code: "MOVE",
                icon: "arrows"
            }
        ]
    };

    const creationMenu = {
        name: "Create",
        description: `These operations introduce new elements in the taxonomy. They will 
                <strong>not</strong> result in data loss.`,
        color: "#0b8829",
        options: [
            {
                name: "Add Child",
                code: "ADD_CHILD",
                icon: "plus-circle",
                description: "Adds a new element to the taxonomy underneath the currently selected item.",
                onShow: () => {
                    resetForm({ concrete: true });
                    calcPreview();
                },
                onToggleConcrete: () => vm.commandParams.concrete = ! vm.commandParams.concrete,
                onChange: () => {
                    const required = [vm.commandParams.name];
                    vm.submitDisabled = _.some(required, _.isEmpty);
                    vm.command = mkUpdCmd();
                }
            }, {
                name: "Add Peer",
                code: "ADD_PEER",
                icon: "plus-circle"
            }, {
                name: "Clone",
                code: "CLONE",
                icon: "clone"
            }
        ]
    };

    const destructiveMenu = {
        name: "Destructive",
        description: `These operations <strong>will</strong> potentially result in data loss and 
                should be used with care`,
        color: "#b40400",
        options: [
            {
                name: "Merge",
                code: "MERGE",
                icon: "code-fork"
            }, {
                name: "Deprecate",
                code: "DEPRECATE",
                icon: "exclamation-triangle"
            }, {
                name: "Destroy",
                code: "REMOVE",
                icon: "trash"
            }
        ]
    };

    vm.menus = [
        creationMenu,
        updateMenu,
        destructiveMenu
    ];

    // --- boot

    vm.$onInit = () => {
        userService
            .whoami()
            .then(u => vm.userName = u.userName);
    };

    vm.$onChanges = (c) => {
        if (c.measurable) {
            vm.onDismiss();
        }
    };


    // --- interact

    vm.toTemplateName = (op) => `wmcc/${op.code}.html`;

    vm.onDismiss = () => {
        vm.mode = modes.MENU;
        vm.command = null;
        vm.preview = null;
    };

    vm.onSelectOperation = (op) => {
        vm.mode = modes.OPERATION;
        vm.selectedOperation = op;
        return _.isFunction(op.onShow)
            ? op.onShow()
            : Promise.resolve();
    };

    vm.onSubmit = () => {
        if (vm.submitDisabled) return;
        vm.onSubmitChange(vm.command)
            .then(vm.onDismiss);
    };
}


controller.$inject = [
    "Notification",
    "ServiceBroker",
    "UserService"
];


const component = {
    bindings,
    controller,
    template
};


export default {
    id: "waltzMeasurableChangeControl",
    component
}
