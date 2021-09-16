# Crypto-Elevate-API

### Requirements

```
scala 2.13
sbt 1.4.6
docker
aws-cli 2.1.10
```

### Build

```shell
sbt docker:publishLocal
```

### Local run

```shell
docker run \
-p 8080:8080 \
-e AWS_REGION="eu-central-1" \
-e AWS_ACCESS_KEY="" \
-e AWS_COGNITO_CLIENT_ID="" \
-e AWS_COGNITO_CLIENT_SECRET="" \
-e AWS_POOL_ID="" \
-e AWS_SECRET_KEY="" \
--name elevate-api \
crypto-elevate-api:0.1.0-SNAPSHOT
```

### Deployment with Terraform-provided environment

```shell
sbt docker:publishLocal
docker tag crypto-elevate-api:0.1.0-SNAPSHOT <ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/elevate-api-<ENVIRONMENT>:latest
aws ecr get-login-password --region <REGION> | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com
docker push <ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/elevate-api-<ENVIRONMENT>:latest
aws ecs update-service --cluster arn:aws:ecs:<REGION>:<ACCOUNT_ID>:cluster/Elevate-<ENVIRONMENT> --service api-<ENVIRONMENT> --force-new-deployment --region=<REGION>
```

#### Infrastructure dependencies

- ECS Cluster
- Cognito User pool