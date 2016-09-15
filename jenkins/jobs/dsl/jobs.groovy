def SampleProjectBuildPipelineFolderName= "Project/Simulation/Cartridge_Management/Project_Sim"
def generateBuildPipelineView = buildPipelineView(SampleProjectBuildPipelineFolderName + "/Project_Pipe")
def generateBuildPackage = freeStyleJob(SampleProjectBuildPipelineFolderName + "/Build_Package")
def generateCodeAnalysis = freeStyleJob(SampleProjectBuildPipelineFolderName + "/Code_Analysis")
def generateCodeDeployment = freeStyleJob(SampleProjectBuildPipelineFolderName + "/Code_Deployment")
def generateWebTesting = mavenJob(SampleProjectBuildPipelineFolderName + "/Web_Testing")

generateBuildPipelineView.with {
	title('Project_Pipe')
	displayedBuilds(3)
	selectedJob(SampleProjectBuildPipelineFolderName + "/Build_Package")
	alwaysAllowManualTrigger()
	showPipelineParameters()
	refreshFrequency(5)
}	

generateBuildPackage.with {
	scm {
		git {
			remote {
				url("http://gitlab/gitlab/dockerwhale/Project-Simulation.git")
				credentials('cf9f3de3-5930-476f-9673-0d56208e7a62')
			}
			branch('*/master')
        }
	}
  	wrappers {
		preBuildCleanup()
		timestamps()
	}
	triggers {
		gitlabPush {
			buildOnMergeRequestEvents(true)
			buildOnPushEvents(true)
			enableCiSkip(true)
			setBuildDescription(true)
			addNoteOnMergeRequest(true)
			rebuildOpenMergeRequest('never')
			addVoteOnMergeRequest(true)
			useCiFeatures(false)
			acceptMergeRequestOnSuccess(false)
          	addNoteOnMergeRequest(true)
		}
	}
	steps {
		maven {
			mavenInstallation('ADOP Maven')
				goals('clean package')
			rootPOM('SampleWebApp/pom.xml')	
		}
	}
	publishers {
		downstreamParameterized {
			trigger('Code_Analysis') {
				condition('SUCCESS')
				parameters {
					predefinedProps([CUSTOM_WORKSPACE: '$WORKSPACE'])
				}
			}
		}
	}
}

generateCodeAnalysis.with{
	customWorkspace('$CUSTOM_WORKSPACE')
  	properties {
        copyArtifactPermissionProperty {
           	projectNames('Code_Deployment')
		}
    }
  
  	parameters {
  		stringParam('CUSTOM_WORKSPACE', null, null)
    }
	steps {
	
    	sonarRunnerBuilder {
			jdk('Inherit from job')
			project('SampleWebApp')
			properties('''# Required metadata
sonar.projectKey=teamwhale.org.whale
sonar.projectName=ProjectSim
sonar.projectVersion=1.0

# Comma-separated paths to directories with sources (required)
sonar.sources=.

# Language
sonar.language=java

# Encoding of the source files
sonar.sourceEncoding=UTF-8''')
			installationName(null)
			sonarScannerName(null)
			javaOpts(null)
			task(null)
			additionalArguments(null)
    	}
    }
	publishers{
		archiveArtifacts {
			pattern('**/*.war')
			onlyIfSuccessful(true)
		}
		downstreamParameterized {
			trigger('Code_Deployment') {
				condition('SUCCESS')
				parameters {
					currentBuild()
				}
			}
		}
	}
  
}

generateCodeDeployment.with{
	label('ansible')
    multiscm {
		git {
			remote {
				//url("http://gitlab/gitlab/dockerwhale/ansible.git")
				url("https://github.com/jmcmanzanilla/ansible.git")
				//credentials('cf9f3de3-5930-476f-9673-0d56208e7a62')
			}
			branch('*/master')
          	relativeTargetDir('ansible')
		}
      	git {
			remote {
				//url("http://gitlab/gitlab/dockerwhale/dockerfile_whale.git")
				url("https://github.com/jmcmanzanilla/dockerfile_whale.git")
				//credentials('cf9f3de3-5930-476f-9673-0d56208e7a62')
			}
			branch('*/master')
   	 	    relativeTargetDir('dockerfile')
		}
	}
  	wrappers {
    	sshAgent('ec2-user')
    }
  
  	steps {
		copyArtifacts('Code_Analysis') {
			includePatterns('SampleWebApp/target/*.war')
			buildSelector {
				latestSuccessful(true)
			}
        }
  		shell('''chmod 400 /workspace/Project_Sim/Code_Deployment/ansible/teamwhale.pem
scp -i /workspace/Project_Sim/Code_Deployment/ansible/teamwhale.pem /workspace/Project_Sim/Code_Deployment/dockerfile/Dockerfile ec2-user@52.53.188.36:~/
scp -i /workspace/Project_Sim/Code_Deployment/ansible/teamwhale.pem /workspace/Project_Sim/Code_Deployment/SampleWebApp/target/*.war ec2-user@52.53.188.36:~/
ansible-playbook ansible/playbook.yml -i ansible/hosts -u ec2-user''')
    }
	publishers {
		downstreamParameterized {
			trigger('Web_Testing') {
				condition('SUCCESS')
				triggerWithNoParameters(true)
			}
		}
    }

}

generateWebTesting.with {
	scm {
		git {
			remote {
				//url("http://gitlab/gitlab/dockerwhale/testing_whale.git")
				url("https://github.com/jmcmanzanilla/testing_whale.git")
				//credentials('cf9f3de3-5930-476f-9673-0d56208e7a62')
			}
			branch('*/master')
        }
	}
	parameters {
		stringParam('CUSTOM_WORKSPACE', null, null)
	}
	triggers {
    	snapshotDependencies(true)
    }
		rootPOM('WebTest/pom.xml')
		goals('clean test')
}