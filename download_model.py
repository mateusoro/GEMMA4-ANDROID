import os
from huggingface_hub import hf_hub_download

print("Downloading gemma-4-E2B-it.litertlm...")
local_path = hf_hub_download(
    repo_id="litert-community/gemma-4-E2B-it-litert-lm",
    filename="gemma-4-E2B-it.litertlm",
    local_dir=".",
    local_dir_use_symlinks=False
)
print(f"Downloaded to: {local_path}")
size = os.path.getsize(local_path)
print(f"File size: {size / (1024*1024*1024):.2f} GB")
