package patches.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.Swabra
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.swabra
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2018_2.failureConditions.BuildFailureOnMetric
import jetbrains.buildServer.configs.kotlin.v2018_2.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.v2018_2.failureConditions.failOnMetricChange
import jetbrains.buildServer.configs.kotlin.v2018_2.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.retryBuild
import jetbrains.buildServer.configs.kotlin.v2018_2.ui.*

/*
This patch script was generated by TeamCity on settings change in UI.
To apply the patch, create a buildType with id = 'PublishHotfixRelease'
in the root project, and delete the patch script.
*/
create(DslContext.projectId, BuildType({
    templates(AbsoluteId("JetBrainsUi_LernaPublish"))
    id("PublishHotfixRelease")
    name = "Publish @hotfix (release-*)"
    paused = true

    allowExternalStatus = true

    params {
        param("lerna.publish.options", "--cd-version patch --preid hotfix --npm-tag hotfix")
        param("vcs.branch.spec", "+:refs/heads/(release-*)")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "Publish"
            id = "RUNNER_1461"
            scriptContent = """
                #!/bin/bash
                set -e -x
                
                # Required for docker
                mkdir -p ~/.ssh/
                touch ~/.ssh/config
                cat << EOT >> ~/.ssh/config
                Host github.com
                    StrictHostKeyChecking no
                    UserKnownHostsFile /dev/null
                EOT
                
                chmod 644 ~/.ssh/config
                
                # GitHub and NPM authorization
                git config user.email "%github.com.builduser.email%"
                git config user.name "%github.com.builduser.name%"
                
                echo "//registry.npmjs.org/:_authToken=%npmjs.com.auth.key%" > ~/.npmrc
                
                node -v
                npm -v
                npm whoami
                
                # Temporary until docker is not updated
                npm config set unsafe-perm true
                
                if [ -n "${'$'}(git status --porcelain)" ]; then
                  echo "Your git status is not clean. Aborting.";
                  exit 1;
                fi
                
                npm install
                npm run bootstrap
                # Reset possibly changed lock to avoid "git status is not clear" error
                git checkout package.json package-lock.json packages/*/package-lock.json
                npm whoami
                npm run release-ci -- %lerna.publish.options%
                
                cat package.json
                
                function publishBuildNumber {
                    local VERSION=${'$'}(node -p 'require("./package.json").version')
                    echo "##teamcity[buildNumber '${'$'}VERSION']"
                }
                
                publishBuildNumber
                
                #chmod 777 ~/.ssh/config
            """.trimIndent()
            dockerImage = "node:12"
            dockerRunParameters = "-v %teamcity.build.workingDir%/npmlogs:/root/.npm/_logs"
        }
        stepsOrder = arrayListOf("RUNNER_1461")
    }

    triggers {
        retryBuild {
            id = "retryBuildTrigger"
            delaySeconds = 60
        }
    }

    failureConditions {
        failOnText {
            id = "BUILD_EXT_184"
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "ERROR:"
            failureMessage = "console.error appeared in log"
            reverse = false
        }
        failOnText {
            id = "BUILD_EXT_185"
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "WARN:"
            failureMessage = "console.warn appeared in log"
            reverse = false
        }
        failOnText {
            id = "BUILD_EXT_186"
            conditionType = BuildFailureOnText.ConditionType.CONTAINS
            pattern = "LOG:"
            failureMessage = "console.log appeared in log"
            reverse = false
        }
        failOnMetricChange {
            id = "BUILD_EXT_187"
            metric = BuildFailureOnMetric.MetricType.INSPECTION_WARN_COUNT
            threshold = 0
            units = BuildFailureOnMetric.MetricUnit.DEFAULT_UNIT
            comparison = BuildFailureOnMetric.MetricComparison.MORE
            compareTo = value()
            param("anchorBuild", "lastSuccessful")
        }
    }

    features {
        sshAgent {
            id = "ssh-agent-build-feature"
            teamcitySshKey = "GitHub ring-ui"
            param("secure:passphrase", "credentialsJSON:60650742-17f8-4b5d-82b2-7108f9408655")
        }
        swabra {
            id = "swabra"
            filesCleanup = Swabra.FilesCleanup.AFTER_BUILD
            forceCleanCheckout = true
            verbose = true
            paths = ".npmrc"
        }
    }

    dependencies {
        snapshot(RelativeId("GeminiTests")) {
            onDependencyFailure = FailureAction.CANCEL
            onDependencyCancel = FailureAction.CANCEL
        }
    }
}))

