pipeline {
    agent any

    environment {
        APP_NAME = 'lms-books'
        DOCKER_IMAGE = 'lms-books'
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

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Unit Tests') {
            when {
                branch 'main'
            }
            steps {
                sh 'mvn clean test -B'
            }
        }

        stage('Build (Staging)') {
            when {
                branch 'staging'
            }
            steps {
                sh 'mvn clean package -DskipTests -B'
            }
        }

        stage('Integration Tests (Staging)') {
            when {
                branch 'staging'
            }
            steps {
                sh '''
                  docker compose up -d postgres_books rabbitmq
                  mvn verify -Pintegration-tests
                '''
            }
            post {
                always {
                    sh 'docker compose down -v'
                }
            }
        }

        stage('Build Docker Image (Staging)') {
            when {
                branch 'staging'
            }
            steps {
                sh 'docker compose -f docker-compose.staging.yml build lms-books-staging'
            }
        }

        stage('Build Docker Image (Production)') {
            when {
                branch 'production'
            }
            steps {
                sh 'docker compose -f docker-compose.production.yml build lms-books-prod'
            }
        }

        stage('Deploy (Staging)') {
            when {
                branch 'staging'
            }
            steps {
                sh '''
                  docker compose -f docker-compose.staging.yml up -d
                '''
            }
        }

        stage('Request Production Approval') {
            when {
                anyOf {
                    branch 'staging'
                    branch 'production'
                }
            }
            steps {
                script {
                    // Send email notification requesting approval
                    emailext(
                        subject: "🚀 PRODUCTION DEPLOYMENT APPROVAL REQUIRED - ${APP_NAME} #${BUILD_NUMBER}",
                        body: """
                            <html>
                            <body style="font-family: Arial, sans-serif;">
                                <h2 style="color: #ff6600;">⚠️ Production Deployment Requires Approval</h2>
                                
                                <table style="border-collapse: collapse; width: 100%; max-width: 600px;">
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Application</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${APP_NAME}</td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Build Number</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">#${BUILD_NUMBER}</td>
                                    </tr>
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Branch</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${BRANCH_NAME}</td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Triggered By</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${BUILD_USER ?: 'Automated'}</td>
                                    </tr>
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Approval Timeout</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${APPROVAL_TIMEOUT_HOURS} hours</td>
                                    </tr>
                                </table>
                                
                                <h3>Build Artifacts:</h3>
                                <ul>
                                    <li>Docker Image: ${DOCKER_IMAGE}:${BUILD_NUMBER}</li>
                                    <li>Target: Production Environment</li>
                                </ul>
                                
                                <h3 style="color: #0066cc;">Action Required:</h3>
                                <p>Please review and approve or reject this deployment:</p>
                                <p>
                                    <a href="${BUILD_URL}input" 
                                       style="background-color: #28a745; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; margin-right: 10px;">
                                        ✅ Review & Approve
                                    </a>
                                </p>
                                
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
                    echo "WAITING FOR PRODUCTION DEPLOYMENT APPROVAL"
                    echo "============================================"
                    echo "An email has been sent to: ${APPROVAL_EMAIL}"
                    echo "Timeout: ${APPROVAL_TIMEOUT_HOURS} hours"
                }
            }
        }

        stage('Production Approval Gate') {
            when {
                anyOf {
                    branch 'staging'
                    branch 'production'
                }
            }
            options {
                timeout(time: "${APPROVAL_TIMEOUT_HOURS}".toInteger(), unit: 'HOURS')
            }
            steps {
                script {
                    def approvalResult = input(
                        id: 'production-deployment-approval',
                        message: '🚀 Approve Production Deployment?',
                        submitter: 'admin,devops,release-manager',
                        submitterParameter: 'approver',
                        parameters: [
                            choice(
                                name: 'APPROVAL_DECISION',
                                choices: ['Approve', 'Reject'],
                                description: 'Approve or Reject this production deployment'
                            ),
                            text(
                                name: 'APPROVAL_NOTES',
                                defaultValue: '',
                                description: 'Optional notes for this deployment decision'
                            )
                        ]
                    )
                    
                    env.APPROVER = approvalResult.approver ?: 'Unknown'
                    env.APPROVAL_DECISION = approvalResult.APPROVAL_DECISION
                    env.APPROVAL_NOTES = approvalResult.APPROVAL_NOTES ?: 'No notes provided'
                    
                    if (env.APPROVAL_DECISION == 'Reject') {
                        // Send rejection notification
                        emailext(
                            subject: "❌ PRODUCTION DEPLOYMENT REJECTED - ${APP_NAME} #${BUILD_NUMBER}",
                            body: """
                                <html>
                                <body style="font-family: Arial, sans-serif;">
                                    <h2 style="color: #dc3545;">❌ Production Deployment Rejected</h2>
                                    <p><strong>Rejected by:</strong> ${env.APPROVER}</p>
                                    <p><strong>Notes:</strong> ${env.APPROVAL_NOTES}</p>
                                    <p><strong>Build:</strong> <a href="${BUILD_URL}">#${BUILD_NUMBER}</a></p>
                                </body>
                                </html>
                            """,
                            to: "${APPROVAL_EMAIL},${DEPLOYER_EMAIL}",
                            mimeType: 'text/html'
                        )
                        
                        error("Production deployment rejected by ${env.APPROVER}. Reason: ${env.APPROVAL_NOTES}")
                    }
                    
                    echo "============================================"
                    echo "PRODUCTION DEPLOYMENT APPROVED"
                    echo "============================================"
                    echo "Approved by: ${env.APPROVER}"
                    echo "Notes: ${env.APPROVAL_NOTES}"
                }
            }
            post {
                aborted {
                    script {
                        emailext(
                            subject: "⏰ PRODUCTION DEPLOYMENT APPROVAL TIMEOUT - ${APP_NAME} #${BUILD_NUMBER}",
                            body: """
                                <html>
                                <body style="font-family: Arial, sans-serif;">
                                    <h2 style="color: #ffc107;">⏰ Approval Timeout Expired</h2>
                                    <p>The production deployment request for <strong>${APP_NAME}</strong> was not approved within ${APPROVAL_TIMEOUT_HOURS} hours.</p>
                                    <p>The deployment has been automatically cancelled.</p>
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

        stage('Deploy (Production)') {
            when {
                allOf {
                    anyOf {
                        branch 'staging'
                        branch 'production'
                    }
                    expression { env.APPROVAL_DECISION == 'Approve' }
                }
            }
            steps {
                script {
                    echo "============================================"
                    echo "DEPLOYING TO PRODUCTION"
                    echo "============================================"
                    echo "Approved by: ${env.APPROVER}"
                    
                    sh '''
                      docker compose -f docker-compose.production.yml up -d
                    '''
                    
                    // Send success notification
                    emailext(
                        subject: "✅ PRODUCTION DEPLOYMENT SUCCESSFUL - ${APP_NAME} #${BUILD_NUMBER}",
                        body: """
                            <html>
                            <body style="font-family: Arial, sans-serif;">
                                <h2 style="color: #28a745;">✅ Production Deployment Successful</h2>
                                <table style="border-collapse: collapse; width: 100%; max-width: 600px;">
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Application</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${APP_NAME}</td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Build</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">#${BUILD_NUMBER}</td>
                                    </tr>
                                    <tr style="background-color: #f2f2f2;">
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Approved By</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${APPROVER}</td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 10px; border: 1px solid #ddd;"><strong>Approval Notes</strong></td>
                                        <td style="padding: 10px; border: 1px solid #ddd;">${APPROVAL_NOTES}</td>
                                    </tr>
                                </table>
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

    post {
        success {
            echo "Pipeline OK para branch: ${env.BRANCH_NAME}"
        }
        failure {
            script {
                echo "Falha na branch ${env.BRANCH_NAME} — rollback executado"
                sh 'docker compose down -v || true'
                
                // Send failure notification
                emailext(
                    subject: "❌ PIPELINE FAILED - ${APP_NAME} #${BUILD_NUMBER}",
                    body: """
                        <html>
                        <body style="font-family: Arial, sans-serif;">
                            <h2 style="color: #dc3545;">❌ Pipeline Failed</h2>
                            <p>The pipeline for <strong>${APP_NAME}</strong> has failed.</p>
                            <p><strong>Branch:</strong> ${BRANCH_NAME}</p>
                            <p><strong>Build:</strong> <a href="${BUILD_URL}">#${BUILD_NUMBER}</a></p>
                            <p>Please check the build logs for more details.</p>
                        </body>
                        </html>
                    """,
                    to: "${APPROVAL_EMAIL},${DEPLOYER_EMAIL}",
                    mimeType: 'text/html',
                    attachLog: true
                )
            }
        }
    }
}
