param([Parameter(Mandatory=$true)][string]$ImagePath)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Storage.StorageFile,Windows.Storage,ContentType=WindowsRuntime]
$null = [Windows.Storage.FileAccessMode,Windows.Storage,ContentType=WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStream,Windows.Storage.Streams,ContentType=WindowsRuntime]
$null = [Windows.Graphics.Imaging.BitmapDecoder,Windows.Foundation,ContentType=WindowsRuntime]
$null = [Windows.Graphics.Imaging.SoftwareBitmap,Windows.Foundation,ContentType=WindowsRuntime]
$null = [Windows.Media.Ocr.OcrEngine,Windows.Foundation,ContentType=WindowsRuntime]
$null = [Windows.Globalization.Language,Windows.Globalization,ContentType=WindowsRuntime]
$awaitMethodTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1
function Wait-WinRtTask($OperationTask, [Type]$ResultTypeTask) {
    $pendingTask = $awaitMethodTask.MakeGenericMethod($ResultTypeTask).Invoke($null, @($OperationTask))
    if (-not $pendingTask.Wait(15000)) { throw 'Local image OCR timed out.' }
    $pendingTask.Result
}
$streamTask = $null
$bitmapTask = $null
try {
    $fileTask = Wait-WinRtTask ([Windows.Storage.StorageFile]::GetFileFromPathAsync($ImagePath)) ([Windows.Storage.StorageFile])
    $streamTask = Wait-WinRtTask ($fileTask.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
    $decoderTask = Wait-WinRtTask ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($streamTask)) ([Windows.Graphics.Imaging.BitmapDecoder])
    $bitmapTask = Wait-WinRtTask ($decoderTask.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
    $engineTask = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage([Windows.Globalization.Language]::new('en-GB'))
    if ($null -eq $engineTask) { throw 'Windows English OCR is unavailable; no remote fallback is used.' }
    $resultTask = Wait-WinRtTask ($engineTask.RecognizeAsync($bitmapTask)) ([Windows.Media.Ocr.OcrResult])
    @{text=$resultTask.Text;language='en-GB';localOnly=$true} | ConvertTo-Json -Compress
} finally {
    if ($null -ne $bitmapTask) { $bitmapTask.Dispose() }
    if ($null -ne $streamTask) { $streamTask.Dispose() }
}
