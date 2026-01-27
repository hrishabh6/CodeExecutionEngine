#!/bin/bash
# Script to pre-pull all Docker images required by CodeExecutionEngine
# Run this before the first submission to avoid timeout issues

set -e

echo "=============================================="
echo "  CodeExecutionEngine - Docker Image Puller  "
echo "=============================================="
echo ""

# Images used by the execution service
IMAGES=(
    "hrishabhjoshi/my-java-runtime:17"
    "hrishabhjoshi/python-runtime:3.9"
)

echo "Pulling required Docker images..."
echo ""

for image in "${IMAGES[@]}"; do
    echo "----------------------------------------"
    echo "Pulling: $image"
    echo "----------------------------------------"
    docker pull "$image"
    echo ""
done

echo "=============================================="
echo "  All images pulled successfully!            "
echo "=============================================="
echo ""

# Verify images are present
echo "Verifying pulled images:"
docker images | grep -E "hrishabhjoshi/(my-java-runtime|python-runtime)" || echo "No images found (unexpected)"

echo ""
echo "You can now run the CodeExecutionEngine without timeout issues."
