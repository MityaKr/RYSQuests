# Runs a dedicated server with two real clients through a shared quest. The three games have to run at the same time,
# which Gradle cannot do with tasks of one project, so this starts them from the arguments Gradle prepares.
# The server folder accepts the Minecraft EULA on its own: it is a local test folder, ignored by git.
param([switch]$RysTeams)

$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$java = (Get-Command java.exe).Source
$utf = [Text.UTF8Encoding]::new($false)
$coreVersion = ((Get-Content "$root\gradle.properties" | Where-Object { $_ -like 'ryscore_version=*' }) -split '=')[1].Trim()
$coreJar = "$root\..\RYSCore\repo\works\repka\ryscore\ryscore-neoforge\$coreVersion\ryscore-neoforge-$coreVersion.jar"
$teamsJar = "$root\..\RYSTeams\build\libs\rysteams-neoforge-1.21.1-0.1.0.jar"

.\gradlew.bat autotestClasses prepareDedicatedServerRun prepareTeamClientARun prepareTeamClientBRun --console=plain | Select-String -Pattern '^BUILD|error:'

foreach ($d in 'run-server', 'run-a', 'run-b') {
    New-Item -ItemType Directory -Force "$root\$d\mods" | Out-Null
    $installed = "$root\$d\mods\rysteams.jar"
    if ($RysTeams) {
        Copy-Item $teamsJar $installed -Force
    } elseif (Test-Path $installed) {
        New-Item -ItemType Directory -Force "$root\_stale" | Out-Null
        Move-Item $installed "$root\_stale\rysteams-$d.jar" -Force
    }
}

[IO.File]::WriteAllText("$root\run-server\eula.txt", "eula=true`n", $utf)
$level = 'autotest_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
[IO.File]::WriteAllText("$root\run-server\server.properties", "online-mode=false`nserver-port=25577`nlevel-name=$level`nspawn-protection=0`ndifficulty=peaceful`nenforce-secure-profile=false`n", $utf)

# TestA stops the server at the end, so it has to be an operator. Offline players get a name based UUID.
$md5 = [Security.Cryptography.MD5]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes('OfflinePlayer:TestA'))
$md5[6] = ($md5[6] -band 0x0f) -bor 0x30
$md5[8] = ($md5[8] -band 0x3f) -bor 0x80
$h = ($md5 | ForEach-Object { $_.ToString('x2') }) -join ''
$uuid = $h.Substring(0, 8) + '-' + $h.Substring(8, 4) + '-' + $h.Substring(12, 4) + '-' + $h.Substring(16, 4) + '-' + $h.Substring(20)
[IO.File]::WriteAllText("$root\run-server\ops.json", "[{`"uuid`":`"$uuid`",`"name`":`"TestA`",`"level`":4,`"bypassesPlayerLimit`":false}]`n", $utf)

$env:MOD_CLASSES = (@('classes\java\main', 'resources\main', 'classes\java\autotest', 'resources\autotest') | Where-Object { Test-Path "$root\build\$_" } | ForEach-Object { "rysquests%%$root\build\$_" }) -join ';'

function Launch($name, $dir) {
    $cp = ("$root\build\moddev\artifacts\neoforge-21.1.265.jar;$coreJar;" + ((Get-Content "$root\build\moddev\${name}LegacyClasspath.txt") -join ';')) -replace '\\', '/'
    $extra = if ($RysTeams) { "-Drysquests.autotest.rysteams=true`n" } else { '' }
    [IO.File]::WriteAllText("$root\$dir\launch.txt", "$extra-cp `"$cp`"`n", $utf)
    Start-Process -FilePath $java -ArgumentList "@$root\$dir\launch.txt", "@$root\build\moddev\${name}RunVmArgs.txt", "@$root\build\moddev\${name}RunProgramArgs.txt" -WorkingDirectory "$root\$dir" -PassThru -WindowStyle Hidden -RedirectStandardOutput "$root\$dir\stdout.log" -RedirectStandardError "$root\$dir\stderr.log"
}

$server = Launch 'dedicatedServer' 'run-server'
$deadline = (Get-Date).AddSeconds(150)
$up = $false
while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
    if ((Test-Path "$root\run-server\stdout.log") -and (Select-String -Path "$root\run-server\stdout.log" -Pattern 'Done \(' -Quiet)) {
        $up = $true
        break
    }
    Start-Sleep -Seconds 2
}
"server up: $up, level $level, RYSTeams: $RysTeams"
if (-not $up) {
    Get-Content "$root\run-server\stderr.log" -Tail 15
    if (-not $server.HasExited) { Stop-Process -Id $server.Id -Force }
    exit 1
}

$a = Launch 'teamClientA' 'run-a'
$b = Launch 'teamClientB' 'run-b'
$deadline = (Get-Date).AddSeconds(420)
while ((Get-Date) -lt $deadline -and -not ($a.HasExited -and $b.HasExited -and $server.HasExited)) {
    Start-Sleep -Seconds 3
}
foreach ($p in @($a, $b, $server)) {
    if (-not $p.HasExited) {
        "still running $($p.Id), stopping"
        Stop-Process -Id $p.Id -Force
    }
}

$passed = $true
foreach ($d in 'run-a', 'run-b') {
    "== $d"
    $log = "$root\$d\logs\latest.log"
    Select-String -Path $log -Pattern '\[autotest\] (PASS|FAIL)|step failed|Loading errors' | Select-Object -First 8 | ForEach-Object { $_.Line.Substring(0, [Math]::Min(200, $_.Line.Length)) }
    (Select-String -Path $log -Pattern '\[autotest\]' | Select-Object -Last 2) | ForEach-Object { $_.Line.Substring($_.Line.IndexOf('[autotest]')) }
    if (-not (Select-String -Path $log -Pattern '\[autotest\] PASS' -Quiet)) { $passed = $false }
}
'== server'
Select-String -Path "$root\run-server\logs\latest.log" -Pattern 'Loaded book|FATAL|NoClassDefFound|ClassNotFound|joined the game' | Select-Object -First 8 | ForEach-Object { $_.Line.Substring(0, [Math]::Min(200, $_.Line.Length)) }
if (-not $passed) { exit 1 }
