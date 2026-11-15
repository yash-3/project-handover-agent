# Inventory runbook

## Deploy a release

1. Build and push the image: `docker build -t registry.example.com/shop/inventory:<version> .` then `docker push`.
2. Update the image tag in `deploy/k8s/deployment.yaml`.
3. Apply the manifests: `kubectl apply -f deploy/k8s/`.
4. Watch the rollout: `kubectl rollout status deployment/inventory`.

Environments are `staging` and `production`, each its own namespace. Deploy to staging first.

## Roll back a bad release

Run `kubectl rollout undo deployment/inventory` in the affected namespace, then check
`kubectl rollout status deployment/inventory`. Rolling back does not undo database changes.

## Secrets

The pods read `DATABASE_URL` and `AMQP_URL` from the Kubernetes secret `inventory-secrets`.
Ask the platform team for the real values; they are not in this repository.
