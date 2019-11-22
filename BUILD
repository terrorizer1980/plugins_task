load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "gerrit_plugin",
)
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:js.bzl", "polygerrit_plugin")

gerrit_plugin(
    name = "task",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: task",
        "Gerrit-ApiVersion: 3.0-SNAPSHOT",
        "Implementation-Title: Task Plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/task",
        "Gerrit-Module: com.googlesource.gerrit.plugins.task.Modules$Module",
    ],
    resource_jars = [":gr-task-plugin-static"],
    resources = glob(["src/main/resources/**/*"]),
)

genrule2(
    name = "gr-task-plugin-static",
    srcs = [":gr-task-plugin"],
    outs = ["gr-task-plugin-static.jar"],
    cmd = " && ".join([
        "mkdir $$TMP/static",
        "cp -r $(locations :gr-task-plugin) $$TMP/static",
        "cd $$TMP",
        "zip -Drq $$ROOT/$@ -g .",
    ]),
)

polygerrit_plugin(
    name = "gr-task-plugin",
    srcs = glob([
        "gr-task-plugin/*.html",
        "gr-task-plugin/*.js",
    ]),
    app = "plugin.html",
)
