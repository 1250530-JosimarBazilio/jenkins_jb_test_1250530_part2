// ===========================================
// Jenkinsfile - Blue/Green Deployment with Automatic Rollback
// ===========================================
// Este pipeline implementa a estratégia Blue/Green deployment
// com smoke tests e rollback automático se forem detectados problemas
//
// Labels de Release:
// - release-strategy: blue-green
// - rollback-enabled: true
// - rollback-trigger: smoke-test-failure | health-check-failure

pipeline {
    agent any

    environment {
        APP_NAME = 'lms-books'
        DOCKER_IMAGE = 'lms-books'
        DOCKER_REGISTRY = "${env.DOCKER_REGISTRY ?: 'localhost:5000'}"
        
        // Blue/Green versioning
        BLUE_VERSION = "${env.BLUE_VERSION ?: 'v1.0.0'}"
        GREEN_VERSION = "${env.GREEN_VERSION ?: 'v2.0.0'}"
        
        // Rollback configuration
        ROLLBACK_ENABLED = 'true'
        SMOKE_TEST_THRESHOLD = '80'
        HEALTH_CHECK_TIMEOUT = '120'
        
        // Email configuration for production approval
        APPROVAL_TIMEOUT_HOURS = '24'
        APPROVAL_EMAIL = "${env.APPROVAL_EMAIL ?: '1250530@isep.ipp.pt'}"
        DEPLOYER_EMAIL = "${env.BUILD_USER_EMAIL ?: '1250530@isep.ipp.pt'}"
    }

    tools {
        jdk 'jdk-17'
        maven 'maven-3'
    }

    options {
        // Abort pipeline if approval is not given within timeout
        timeout(time: 48, unit: 'HOURS')
    }

    parameters {
        choice(
            name: 'DEPLOYMENT_SLOT',
            choices: ['green', 'blue'],
            description: 'Target deployment slot'
        )
        booleanParam(
            name: 'SIMULATE_ERROR',
            defaultValue: false,
            description: 'Simulate errors in GREEN deployment for rollback testing'
        )
        booleanParam(
            name: 'SKIP_SMOKE_TESTS',
            defaultValue: false,
            description: 'Skip smoke tests (not recommended)'
        )
        booleanParam(
            name: 'REQUIRE_APPROVAL',
            defaultValue: true,
            description: 'Require manual email approval before switching traffic (production safety)'
        )
        choice(
            name: 'PLATFORM',
            choices: ['docker', 'k8s'],
            description: 'Deployment platform'
        )
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    echo "============================================"
                    echo "BLUE/GREEN DEPLOYMENT PIPELINE"
                    echo "============================================"
                    echo "Target Slot: ${params.DEPLOYMENT_SLOT}"
                    echo "Simulate Error: ${params.SIMULATE_ERROR}"
                    echo "Require Approval: ${params.REQUIRE_APPROVAL}"
                    echo "Platform: ${params.PLATFORM}"
                    echo "============================================"
                }
            }
        }

        stage('Build & Unit Tests') {
            steps {
                sh 'mvn clean test -B'
            }
            post {
                failure {
                    script {
                        currentBuild.description = "Build failed - Unit tests"
                    }
                }
            }
        }

        stage('Package Application') {
            steps {
                sh 'mvn package -DskipTests -B'
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    def slot = params.DEPLOYMENT_SLOT
                    def version = slot == 'green' ? env.GREEN_VERSION : env.BLUE_VERSION
                    def profile = params.SIMULATE_ERROR && slot == 'green' ? 'green-error' : slot
                    
                    echo "Building Docker image for ${slot} (version: ${version})"
                    echo "Spring Profile: ${profile}"
                    
                    sh """
                        docker build \
                            --build-arg SPRING_PROFILES_ACTIVE=postgres,${profile} \
                            --build-arg DEPLOYMENT_SLOT=${slot} \
                            --build-arg VERSION=${version} \
                            --label "deployment.slot=${slot}" \
                            --label "deployment.version=${version}" \
                            --label "deployment.release-strategy=blue-green" \
                            --label "deployment.rollback-enabled=true" \
                            --label "deployment.simulate-error=${params.SIMULATE_ERROR}" \
                            -t ${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${version} \
                            -t ${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${slot}-latest \
                            .
                    """
                }
            }
        }

        stage('Push Docker Image') {
            when {
                expression { env.DOCKER_REGISTRY != 'localhost:5000' }
            }
            steps {
                script {
                    def slot = params.DEPLOYMENT_SLOT
                    def version = slot == 'green' ? env.GREEN_VERSION : env.BLUE_VERSION
                    
                    sh """
                        docker push ${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${version}
                        docker push ${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${slot}-latest
                    """
                }
            }
        }

        stage('Deploy to Target Slot') {
            steps {
                script {
                    def slot = params.DEPLOYMENT_SLOT
                    def version = slot == 'green' ? env.GREEN_VERSION : env.BLUE_VERSION
                    def profile = params.SIMULATE_ERROR && slot == 'green' ? 'green-error' : slot
                    
                    echo "Deploying to ${slot} slot..."
                    
                    if (params.PLATFORM == 'docker') {
                        sh """
                            # Set environment variables for docker-compose
                            export ${slot.toUpperCase()}_VERSION=${version}
                            export SPRING_PROFILES_ACTIVE=postgres,${profile}
                            export DEPLOYMENT_SLOT=${slot}
                            
                            # Deploy only the target slot
                            docker compose -f docker-compose.bluegreen.yml up -d lms-books-${slot}
                        """
                    } else {
                        sh """
                            # Kubernetes deployment
                            export ${slot.toUpperCase()}_VERSION=${version}
                            
                            # Apply deployment
                            envsubst < k8s/lms-books-bluegreen.yaml | kubectl apply -f - --selector=slot=${slot}
                            
                            # Wait for rollout
                            kubectl rollout status deployment/lms-books-${slot} -n lms-books --timeout=${HEALTH_CHECK_TIMEOUT}s
                        """
                    }
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def slot = params.DEPLOYMENT_SLOT
                    def healthUrl = slot == 'green' ? 'http://localhost:8091' : 'http://localhost:8090'
                    
                    if (params.PLATFORM == 'k8s') {
                        healthUrl = "http://lms-books-${slot}.lms-books.svc.cluster.local:8081"
                    }
                    
                    echo "Waiting for ${slot} to be healthy..."
                    
                    def healthy = false
                    def attempts = 0
                    def maxAttempts = 30
                    
                    while (!healthy && attempts < maxAttempts) {
                        try {
                            def response = sh(
                                script: "curl -s -o /dev/null -w '%{http_code}' ${healthUrl}/api/books/health",
                                returnStdout: true
                            ).trim()
                            
                            if (response == '200') {
                                healthy = true
                                echo "Health check passed!"
                            }
                        } catch (Exception e) {
                            echo "Health check attempt ${attempts + 1}/${maxAttempts} failed"
                        }
                        
                        if (!healthy) {
                            sleep(5)
                            attempts++
                        }
                    }
                    
                    if (!healthy) {
                        error("Health check failed after ${maxAttempts} attempts")
                    }
                }
            }
        }

        stage('Smoke Tests') {
            when {
                expression { !params.SKIP_SMOKE_TESTS }
            }
            steps {
                script {
                    def slot = params.DEPLOYMENT_SLOT
                    def greenUrl = 'http://localhost:8091'
                    def blueUrl = 'http://localhost:8090'
                    
                    if (params.PLATFORM == 'k8s') {
                        greenUrl = 'http://localhost:30091'  // NodePort
                        blueUrl = 'http://localhost:30090'
                    }
                    
                    echo "Running smoke tests..."
                    
                    def result = sh(
                        script: """
                            chmod +x scripts/smoke-test-rollback.sh
                            ./scripts/smoke-test-rollback.sh ${params.PLATFORM} ${greenUrl} ${blueUrl}
                        """,
                        returnStatus: true
                    )
                    
                    if (result != 0) {
                        echo "Smoke tests failed - Rollback will be triggered"
                        env.ROLLBACK_REQUIRED = 'true'
                        error("Smoke tests failed")
                    }
                }
            }
            post {
                failure {
                    script {
                        env.ROLLBACK_REQUIRED = 'true'
                    }
                }
            }
        }

        stage('Request Production Approval') {
            when {
                allOf {
                    expression { params.DEPLOYMENT_SLOT == 'green' }
                    expression { env.ROLLBACK_REQUIRED != 'true' }
                    expression { params.REQUIRE_APPROVAL == true }
                }
            }
            steps {
                script {
                    def version = env.GREEN_VERSION
                    
                    // Send email notification requesting approval
                    emailext(
                        subject: "🚀 BLUE/GREEN PRODUCTION APPROVAL REQUIRED - ${APP_NAME} ${version}",
                        body: """
                            <html>
                            <body style="font-family: Arial, sans-serif;">
                                <h2 style="color: #ff6600;">⚠️ Production Traffic Switch Requires Approval</h2>
                                
                                <div style="background-color: #e7f3ff; padding: 15px; border-radius: 5px; margin-bottom: 20px;">
                                    <h3 style="margin: 0; color: #0066cc;">Blue/Green Deployment</h3>
                                    <p style="margin: 5px 0;">Ready to switch traffic from <strong style="color: #007bff;">BLUE</strong> to <strong style="color: #28a745;">GREEN</strong></p>
                                </div>
                                
                                <table style="border-collapse: collapse; width: 100%; max-width: 600px;">
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Application</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${APP_NAME}</td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Current Slot</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><span style="color: #007bff;">● BLUE (${BLUE_VERSION})</span></td>
                                    </tr>
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Target Slot</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><span style="color: #28a745;">● GREEN (${version})</span></td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Build Number</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">#${BUILD_NUMBER}</td>
                                    </tr>
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Platform</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${params.PLATFORM}</td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Smoke Tests</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">✅ Passed</td>
                                    </tr>
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Health Check</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">✅ Healthy</td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Approval Timeout</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${APPROVAL_TIMEOUT_HOURS} hours</td>
                                    </tr>
                                </table>
                                
                                <h3 style="color: #0066cc; margin-top: 20px;">Action Required:</h3>
                                <p>Please review and approve or reject this traffic switch:</p>
                                <p>
                                    <a href="${BUILD_URL}input" 
                                       style="background-color: #28a745; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px;">
                                        ✅ Review & Approve Traffic Switch
                                    </a>
                                </p>
                                
                                <div style="background-color: #fff3cd; padding: 15px; border-radius: 5px; margin-top: 20px;">
                                    <strong>⚠️ Note:</strong> If rejected or timed out, the GREEN deployment will remain available but traffic will continue to flow to BLUE. 
                                    Automatic rollback is available if issues are detected.
                                </div>
                                
                                <hr style="margin-top: 30px;">
                                <p style="color: #666; font-size: 12px;">
                                    This is an automated message from Jenkins CI/CD Pipeline.<br>
                                    Build URL: <a href="${BUILD_URL}">${BUILD_URL}</a>
                                </p>
                            </body>
                            </html>
                        """,
                        to: "${APPROVAL_EMAIL}",
                        from: "${DEPLOYER_EMAIL}",
                        mimeType: 'text/html',
                        attachLog: true
                    )
                    
                    echo "============================================"
                    echo "WAITING FOR PRODUCTION TRAFFIC SWITCH APPROVAL"
                    echo "============================================"
                    echo "An email has been sent to: ${APPROVAL_EMAIL}"
                    echo "Timeout: ${APPROVAL_TIMEOUT_HOURS} hours"
                }
            }
        }

        stage('Production Approval Gate') {
            when {
                allOf {
                    expression { params.DEPLOYMENT_SLOT == 'green' }
                    expression { env.ROLLBACK_REQUIRED != 'true' }
                    expression { params.REQUIRE_APPROVAL == true }
                }
            }
            steps {
                timeout(time: Integer.parseInt(APPROVAL_TIMEOUT_HOURS), unit: 'HOURS') {
                    script {
                        def approvalResult = input(
                            id: 'bluegreen-traffic-switch-approval',
                            message: '🚀 Approve Traffic Switch to GREEN?',
                            submitter: 'admin,devops,release-manager',
                            submitterParameter: 'approver',
                            parameters: [
                            choice(
                                name: 'APPROVAL_DECISION',
                                choices: ['Approve', 'Reject'],
                                description: 'Approve or Reject the traffic switch to GREEN'
                            ),
                            text(
                                name: 'APPROVAL_NOTES',
                                defaultValue: '',
                                description: 'Optional notes for this deployment decision'
                            ),
                            booleanParam(
                                name: 'ENABLE_CANARY',
                                defaultValue: false,
                                description: 'Enable canary deployment (gradual traffic shift) instead of full switch'
                            )
                        ]
                    )
                    
                    env.APPROVER = approvalResult.approver ?: 'Unknown'
                    env.APPROVAL_DECISION = approvalResult.APPROVAL_DECISION
                    env.APPROVAL_NOTES = approvalResult.APPROVAL_NOTES ?: 'No notes provided'
                    env.ENABLE_CANARY = approvalResult.ENABLE_CANARY
                    
                    if (env.APPROVAL_DECISION == 'Reject') {
                        // Send rejection notification
                        emailext(
                            subject: "❌ BLUE/GREEN TRAFFIC SWITCH REJECTED - ${APP_NAME} #${BUILD_NUMBER}",
                            body: """
                                <html>
                                <body style="font-family: Arial, sans-serif;">
                                    <h2 style="color: #dc3545;">❌ Traffic Switch Rejected</h2>
                                    <p>Traffic will continue to flow to <strong style="color: #007bff;">BLUE</strong>.</p>
                                    <p><strong>Rejected by:</strong> ${env.APPROVER}</p>
                                    <p><strong>Notes:</strong> ${env.APPROVAL_NOTES}</p>
                                    <p><strong>Build:</strong> <a href="${BUILD_URL}">#${BUILD_NUMBER}</a></p>
                                    <div style="background-color: #e7f3ff; padding: 10px; border-radius: 5px; margin-top: 15px;">
                                        <strong>Note:</strong> The GREEN deployment is still available for testing.
                                    </div>
                                </body>
                                </html>
                            """,
                            to: "${APPROVAL_EMAIL},${DEPLOYER_EMAIL}",
                            mimeType: 'text/html'
                        )
                        
                        currentBuild.description = "Traffic switch rejected by ${env.APPROVER}"
                        currentBuild.result = 'UNSTABLE'
                        env.SKIP_TRAFFIC_SWITCH = 'true'
                    } else {
                        echo "============================================"
                        echo "TRAFFIC SWITCH APPROVED"
                        echo "============================================"
                        echo "Approved by: ${env.APPROVER}"
                        echo "Notes: ${env.APPROVAL_NOTES}"
                        echo "Canary Mode: ${env.ENABLE_CANARY}"
                    }
                }
            }
            post {
                aborted {
                    script {
                        emailext(
                            subject: "⏰ BLUE/GREEN APPROVAL TIMEOUT - ${APP_NAME} #${BUILD_NUMBER}",
                            body: """
                                <html>
                                <body style="font-family: Arial, sans-serif;">
                                    <h2 style="color: #ffc107;">⏰ Approval Timeout Expired</h2>
                                    <p>The traffic switch request was not approved within ${APPROVAL_TIMEOUT_HOURS} hours.</p>
                                    <p>Traffic will continue to flow to <strong style="color: #007bff;">BLUE</strong>.</p>
                                    <p><strong>Build:</strong> <a href="${BUILD_URL}">#${BUILD_NUMBER}</a></p>
                                </body>
                                </html>
                            """,
                            to: "${APPROVAL_EMAIL},${DEPLOYER_EMAIL}",
                            mimeType: 'text/html'
                        )
                    }
                }
            }
        }

        stage('Switch Traffic to GREEN') {
            when {
                allOf {
                    expression { params.DEPLOYMENT_SLOT == 'green' }
                    expression { env.ROLLBACK_REQUIRED != 'true' }
                    expression { env.SKIP_TRAFFIC_SWITCH != 'true' }
                    anyOf {
                        expression { params.REQUIRE_APPROVAL == false }
                        expression { env.APPROVAL_DECISION == 'Approve' }
                    }
                }
            }
            steps {
                script {
                    echo "Switching traffic to GREEN..."
                    echo "Approved by: ${env.APPROVER ?: 'Auto-approved (approval disabled)'}"
                    
                    sh """
                        chmod +x scripts/bluegreen-switch.sh
                        ./scripts/bluegreen-switch.sh green ${params.PLATFORM}
                    """
                    
                    echo "Traffic switched to GREEN successfully!"
                    
                    // Send success notification
                    emailext(
                        subject: "✅ TRAFFIC SWITCHED TO GREEN - ${APP_NAME} #${BUILD_NUMBER}",
                        body: """
                            <html>
                            <body style="font-family: Arial, sans-serif;">
                                <h2 style="color: #28a745;">✅ Traffic Successfully Switched to GREEN</h2>
                                <table style="border-collapse: collapse; width: 100%; max-width: 600px;">
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Application</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${APP_NAME}</td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Active Slot</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><span style="color: #28a745;">● GREEN (${GREEN_VERSION})</span></td>
                                    </tr>
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Approved By</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${APPROVER ?: 'Auto-approved'}</td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Notes</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${APPROVAL_NOTES ?: 'N/A'}</td>
                                    </tr>
                                </table>
                                <div style="background-color: #d4edda; padding: 15px; border-radius: 5px; margin-top: 20px;">
                                    <strong>🔄 Rollback:</strong> If issues are detected, run the rollback script to switch back to BLUE.
                                </div>
                            </body>
                            </html>
                        """,
                        to: "${APPROVAL_EMAIL},${DEPLOYER_EMAIL}",
                        mimeType: 'text/html'
                    )
                }
            }
        }

        stage('Automatic Rollback') {
            when {
                expression { env.ROLLBACK_REQUIRED == 'true' }
            }
            steps {
                script {
                    echo "============================================"
                    echo "AUTOMATIC ROLLBACK TRIGGERED"
                    echo "============================================"
                    
                    sh """
                        chmod +x scripts/bluegreen-rollback.sh
                        ./scripts/bluegreen-rollback.sh ${params.PLATFORM}
                    """
                    
                    currentBuild.description = "Rollback executed - Tests failed"
                    currentBuild.result = 'UNSTABLE'
                }
            }
        }

        stage('Post-Deployment Verification') {
            when {
                expression { env.ROLLBACK_REQUIRED != 'true' }
            }
            steps {
                script {
                    echo "Verifying deployment..."
                    
                    def activeSlot = sh(
                        script: """
                            if [ "${params.PLATFORM}" == "docker" ]; then
                                grep -B1 "weight: 100" traefik/dynamic/bluegreen.yml | head -1 | grep -o 'blue\\|green' || echo "unknown"
                            else
                                kubectl get svc lms-books-service -n lms-books -o jsonpath='{.spec.selector.slot}'
                            fi
                        """,
                        returnStdout: true
                    ).trim()
                    
                    echo "Active deployment slot: ${activeSlot}"
                    
                    // Add labels/annotations
                    if (params.PLATFORM == 'k8s') {
                        sh """
                            kubectl annotate deployment lms-books-${params.DEPLOYMENT_SLOT} -n lms-books \
                                deployment.kubernetes.io/deployed-by="jenkins" \
                                deployment.kubernetes.io/deployed-at="\$(date -Iseconds)" \
                                deployment.kubernetes.io/build-number="${env.BUILD_NUMBER}" \
                                --overwrite
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                if (env.ROLLBACK_REQUIRED == 'true') {
                    echo "Pipeline completed with rollback"
                } else {
                    echo "============================================"
                    echo "DEPLOYMENT SUCCESSFUL"
                    echo "============================================"
                    echo "Slot: ${params.DEPLOYMENT_SLOT}"
                    echo "Version: ${params.DEPLOYMENT_SLOT == 'green' ? env.GREEN_VERSION : env.BLUE_VERSION}"
                    echo "============================================"
                }
            }
        }
        failure {
            script {
                echo "============================================"
                echo "PIPELINE FAILED - EXECUTING ROLLBACK"
                echo "============================================"
                
                // Attempt automatic rollback on failure
                sh """
                    if [ -f "scripts/bluegreen-rollback.sh" ]; then
                        chmod +x scripts/bluegreen-rollback.sh
                        ./scripts/bluegreen-rollback.sh ${params.PLATFORM} || true
                    fi
                """
                
                // Cleanup
                sh 'docker compose -f docker-compose.bluegreen.yml down lms-books-${params.DEPLOYMENT_SLOT} || true'
            }
        }
        always {
            // Archive test results
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
            
            // Cleanup workspace
            cleanWs(cleanWhenNotBuilt: false, deleteDirs: true, disableDeferredWipeout: true)
        }
    }
}
}