param([string]$proj, [string]$out)
# ASCII only script.
# docs/design.md section 1.1 (K) and 1.2 (E) tables -> questions_K.tsv, questions_E.tsv
# columns (tab separated): id, instruction, true criteria, false criteria
$enc = New-Object System.Text.UTF8Encoding $false
$sec = ''
$k = @(); $e = @()
foreach ($l in [IO.File]::ReadAllLines("$proj\docs\design.md", [Text.Encoding]::UTF8)) {
  if ($l -like '### 1.1*') { $sec = 'K'; continue }
  if ($l -like '### 1.2*') { $sec = 'E'; continue }
  if ($l -like '## 2.*') { $sec = ''; continue }
  if ($sec -and $l -match '^\| (I1|I2a|I2b|I3|I4|F1|F2|F3|F4a|F4b) \|') {
    $c = $l -split '\|'
    $row = ($c[1].Trim(), $c[2].Trim(), $c[3].Trim(), $c[4].Trim()) -join "`t"
    if ($sec -eq 'K') { $k += $row } else { $e += $row }
  }
}
New-Item -ItemType Directory -Force -Path $out | Out-Null
[IO.File]::WriteAllLines("$out\questions_K.tsv", $k, $enc)
[IO.File]::WriteAllLines("$out\questions_E.tsv", $e, $enc)
"questions K: $($k.Count) / E: $($e.Count)"
