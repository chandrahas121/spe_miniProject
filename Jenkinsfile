// Declarative pipeline: checkout -> build -> test -> docker build -> push -> deploy
//
// Jenkins credentials used (nothing secret lives in this repo):
//   dockerhub-creds  Username with password   Docker Hub username + access token
//   notify-email     Secret text              address that gets the build status mail
pipeline {
    agent any

    options {
        skipDefaultCheckout(true)          // the Checkout stage below does it
        disableConcurrentBuilds()          // two deploys at once would fight over the ports
        timestamps()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    triggers {
        githubPush()                       // GitHub webhook
        pollSCM('H/5 * * * *')             // fallback if the webhook cannot reach Jenkins
    }

    environment {
        DOCKERHUB     = credentials('dockerhub-creds')    // gives DOCKERHUB_USR and DOCKERHUB_PSW
        NOTIFY_EMAIL  = credentials('notify-email')
        IMAGE_REPO    = 'calculator'
        // keep docker login out of the jenkins user's home, it is removed after the build
        DOCKER_CONFIG = "${WORKSPACE}/.docker"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Build') {
            steps {
                // tests run in the next stage
                sh './mvnw -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                // a failing test fails the build
                sh './mvnw -B test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    IMAGE="$DOCKERHUB_USR/$IMAGE_REPO"
                    docker build -t "$IMAGE:$BUILD_NUMBER" -t "$IMAGE:latest" .
                '''
            }
        }

        stage('Push') {
            steps {
                sh '''
                    IMAGE="$DOCKERHUB_USR/$IMAGE_REPO"
                    echo "$DOCKERHUB_PSW" | docker login -u "$DOCKERHUB_USR" --password-stdin
                    docker push "$IMAGE:$BUILD_NUMBER"
                    docker push "$IMAGE:latest"
                '''
            }
        }

        stage('Deploy') {
            steps {
                // safe to re-run: compose only recreates containers whose image or config changed
                sh '''
                    export IMAGE_NAME="$DOCKERHUB_USR/$IMAGE_REPO"
                    export IMAGE_TAG="$BUILD_NUMBER"
                    docker compose up -d --no-build --remove-orphans

                    echo "waiting for the stack to report healthy..."
                    for i in $(seq 1 30); do
                        if curl -fs http://localhost:8088/actuator/health | grep -q '"UP"'; then
                            echo "health check passed"
                            docker compose ps
                            exit 0
                        fi
                        sleep 2
                    done

                    echo "health check failed"
                    docker compose ps
                    docker compose logs --tail=50
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            sh 'docker logout || true'
            sh 'rm -rf "$DOCKER_CONFIG"'
        }
        success {
            script { notifyBuild('SUCCESS') }
        }
        failure {
            script { notifyBuild('FAILURE') }
        }
    }
}

// a broken mail setup should not turn a good deploy into a failed build
def notifyBuild(String status) {
    try {
        mail to: env.NOTIFY_EMAIL,
             subject: "[Jenkins] ${env.JOB_NAME} #${env.BUILD_NUMBER} - ${status}",
             body: """Build ${status}

Job:    ${env.JOB_NAME}
Build:  #${env.BUILD_NUMBER}
Image:  ${env.DOCKERHUB_USR}/${env.IMAGE_REPO}:${env.BUILD_NUMBER}
Log:    ${env.BUILD_URL}console
"""
    } catch (err) {
        echo "Could not send the status email: ${err.message}"
    }
}
