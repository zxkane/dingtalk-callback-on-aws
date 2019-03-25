## A Dingtalk(钉钉) callback on AWS

The program provides a HTTP API endpoint to receive kinds of dingtalk callback events and persist them in AWS DynamoDB, including `BPM events`, `Organization events` and so on.

It is written by Kotlin, and leverages below AWS services,

- [Lambda](https://aws.amazon.com/lambda/)
- [DynamoDB](https://aws.amazon.com/dynamodb/)
- [CloudFormation](https://aws.amazon.com/cloudformation/)
- [API Gateway](https://aws.amazon.com/api-gateway/)
- [CodePipeline](https://aws.amazon.com/codepipeline/)
- [Systems Manager](https://aws.amazon.com/systems-manager/)
- [S3](https://aws.amazon.com/s3/)
- [CloudWatch](https://aws.amazon.com/cloudwatch/)
- [KMS](https://aws.amazon.com/kms/)

### How to deploy this program

#### Prerequisites

1. Get the **corpid** of your dingtalk's organization in [open dev platform](https://open-dev.dingtalk.com/#/index)
1. Create secure parameters named `DD_TOKEN`, `DD_AES_TOKEN` and `DD_CORPID`(from step 1) in [Systems Manager
](https://ap-southeast-1.console.aws.amazon.com/systems-manager/parameters?region=ap-southeast-1)
1. Create a S3 bucket(say `my-deploy-bucket`) for deployment
1. [Optional] Install and configure [SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html) for local deployment

#### Build, Test and package

```bash
# build the source
```

####  Deploy via SAM cli

```bash
# package the lambda functions
sam package --output-template-file packaged.yaml \
    --s3-bucket my-deploy-bucket --template-file template-sam.yaml
    
# deploy the lambda function, api gateway, dybnamodb
sam deploy --template-file ./packaged.yaml \
    --stack-name my-dingtalk-callback --capabilities CAPABILITY_IAM
```

#### Deploy via Code pipeline
TBA

### Post deployment actions

1. Get `id of api gateway of AWS` created by above deployment
1. Use [dingtalk API](https://open-doc.dingtalk.com/microapp/serverapi2/pwz3r5) to register/update this serverless API gateway endpoint as callback of dingtalk events.

For example,

```bash
curl -X POST \
  'https://oapi.dingtalk.com/call_back/update_call_back?access_token=<your token>' \
  -H 'Content-Type: application/json' \
  -d '{
    "call_back_tag": [
        "bpms_task_change",
        "bpms_instance_change"
    ],
    "token": "<token created in prerequisites step 2>",
    "aes_key": "<aes token created in prerequisites step 2>",
    "url": "https://<id of api gateway created by above deployment>.execute-api.<your region>.amazonaws.com/v1/dingtalk"
}' 

```