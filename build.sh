docker build -t quay.io/johannesschmidt/gps-tools-backend  -f src/main/docker/Dockerfile .
docker push quay.io/johannesschmidt/gps-tools-backend

docker buildx build --load --platform linux/arm64 -f src/main/docker/Dockerfile.arm64v8 -t quay.io/johannesschmidt/gps-tools-backend:arm64v8 .
docker push quay.io/johannesschmidt/gps-tools-backend:arm64v8
