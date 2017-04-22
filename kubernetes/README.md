# YOPA on Kubernetes

## Deploying YOPA to Kubernetes

From the root of the repository (i.e. not here!):

```
kubectl apply -f kubernetes/
```

## Configuration

The configuration for YOPA is deployed via `yopa-configmap.yml` as a Kubernetes [ConfigMap](https://kubernetes.io/docs/tasks/configure-pod-container/configmap/). There is a default configuration provided, which is just a copy of the sample configuration in the root of to repo.

To update the configuration, make the desired changes to `yopa-configmap.yml` and execute:

```
kubectl apply -f yopa-configmap.yml
```

Note that the running deployment **will not** pick up the configuration changes automatically - you'll need to redeploy (or kill the pods, or whatever).

## Accessing YOPA within Kubenetes

Internally, you can refer to YOPA via it's service hostname (`yopa-service`).

For example, we can run a pod with the AWS CLI in Kubernetes:

```
kubectl run aws-cli  -it --image=garland/aws-cli-docker sh
```

After exporting some dummy values to make the AWS CLI happy...

```
export AWS_DEFAULT_REGION=dummy
export AWS_ACCESS_KEY_ID=00000
export AWS_SECRET_ACCESS_KEY=0000
```

...you should be able to use the cli to hit YOPA. E.g.

```
aws --endpoint-url=http://yopa-service:47196 sns list-topics
{
    "Topics": [
        {
            "TopicArn": "arn:aws:sns:yopa-local:000000000000:test-topic-without-subscription"
        },
        {
            "TopicArn": "arn:aws:sns:yopa-local:000000000000:test-topic-with-subscriptions"
        }
    ]
}
```

```
aws --endpoint-url=http://yopa-service:47195 sqs list-queues
{
    "QueueUrls": [
        "http://localhost:47195/queue/test-standalone-queue",
        "http://localhost:47195/queue/test-subscribed-queue-standard",
        "http://localhost:47195/queue/test-subscribed-queue-raw"
    ]
}
```

...etc.

