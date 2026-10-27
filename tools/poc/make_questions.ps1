param([string]$proj, [string]$out)
# ASCII only script.
# docs/design.md section 1.1 (K), 1.2 (E), 1.3 (K2) tables -> questions_K.tsv, questions_E.tsv, questions_K2.tsv
# columns (tab separated): id, instruction, true criteria, false criteria
$enc = New-Object System.Text.UTF8Encoding $false
$sec = ''
$k = @(); $e = @(); $k2 = @()
foreach ($l in [IO.File]::ReadAllLines("$proj\docs\design.md", [Text.Encoding]::UTF8)) {
  if ($l -like '### 1.1*') { $sec = 'K'; continue }
  if ($l -like '### 1.2*') { $sec = 'E'; continue }
  if ($l -like '### 1.3*') { $sec = 'K2'; continue }
  if ($l -like '## 2.*') { $sec = ''; continue }
  if ($sec -and $l -match '^\| (I1|I2a|I2b|I3|I4|F1|F2|F3|F4a|F4b) \|') {
    $c = $l -split '\|'
    $row = ($c[1].Trim(), $c[2].Trim(), $c[3].Trim(), $c[4].Trim()) -join "`t"
    if ($sec -eq 'K') { $k += $row } elseif ($sec -eq 'E') { $e += $row } else { $k2 += $row }
  }
}
New-Item -ItemType Directory -Force -Path $out | Out-Null
[IO.File]::WriteAllLines("$out\questions_K.tsv", $k, $enc)
[IO.File]::WriteAllLines("$out\questions_E.tsv", $e, $enc)
[IO.File]::WriteAllLines("$out\questions_K2.tsv", $k2, $enc)
"questions K: $($k.Count) / E: $($e.Count) / K2: $($k2.Count)"
