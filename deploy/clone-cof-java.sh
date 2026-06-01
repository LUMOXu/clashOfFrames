#!/usr/bin/env bash
set -euo pipefail
export GIT_SSH_COMMAND="ssh -i /root/.ssh/id_ed25519_github -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new"
rm -rf /opt/cof-java
git clone --depth 1 -b feature-dyu git@github.com:LUMOXu/clashOfFrames.git /opt/cof-java
echo CLONE_OK
