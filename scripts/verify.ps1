$ErrorActionPreference='Stop'
Push-Location (Join-Path $PSScriptRoot '..\blockly-editor')
try {
    npm.cmd ci
    if($LASTEXITCODE -ne 0){throw 'npm ci failed'}
    npm.cmd test
    if($LASTEXITCODE -ne 0){throw 'Frontend tests failed'}
    npm.cmd run build
    if($LASTEXITCODE -ne 0){throw 'Editor build failed'}
}finally{Pop-Location}
Push-Location (Join-Path $PSScriptRoot '..')
try {
    .\gradlew.bat :engine:test :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease --max-workers=2
    if($LASTEXITCODE -ne 0){throw 'Android verification failed'}
    Get-FileHash -Algorithm SHA256 -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk'
}finally{Pop-Location}
