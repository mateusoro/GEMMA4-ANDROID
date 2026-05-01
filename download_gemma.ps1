$ProgressPreference = 'SilentlyContinue'
$url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
$outFile = "C:\temp_gemma4.litertlm"
Write-Host "Downloading gemma-4-E2B-it.litertlm..."
$startTime = Get-Date
Invoke-WebRequest -Uri $url -OutFile $outFile
$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds
$size = (Get-Item $outFile).Length
Write-Host "Download complete: $size bytes in $([math]::Round($duration, 1))s"
Write-Host "SHA256 needed: ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42"