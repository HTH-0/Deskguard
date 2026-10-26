param([string]$run)
# ASCII only script. Exploratory re-analysis of existing PoC results (no API calls).
$enc = New-Object System.Text.UTF8Encoding $false
$data = Get-Content "$run\data.tsv" -Encoding UTF8 | ForEach-Object {
  $f = $_ -split "`t"
  [pscustomobject]@{ id = $f[0]; group = $f[1]; label = $f[2]; tag = $f[3]; text = $f[4] }
}
$qids = 'I1','I2a','I2b','I3','I4','F1','F2','F3','F4a','F4b'
$out = New-Object System.Collections.ArrayList
function Out($s) { [void]$out.Add($s) }

function LoadMeans($path) {
  $rows = Import-Csv $path -Encoding UTF8 | Where-Object { $_.status -eq '200' }
  $m = @{}
  foreach ($g in ($rows | Group-Object id)) {
    $h = @{}
    foreach ($q in $qids) { $h[$q] = (@($g.Group | ForEach-Object { [double]$_.$q }) | Measure-Object -Average).Average }
    $m[$g.Name] = $h
  }
  return $m
}
function Fmt($h) { ($qids | ForEach-Object { "{0}={1:N2}" -f $_, $h[$_] }) -join ' ' }
function Flag($h, $thr) { foreach ($q in $qids) { if ($h[$q] -ge $thr[$q]) { return $true } } return $false }

$K = LoadMeans "$run\result_K.csv"
$E = LoadMeans "$run\result_E.csv"

Out "== 1. per-question means for key sentences =="
foreach ($id in 'A-15','A-16','A-18','F4-05','G-15','B-02','G-14') {
  Out ("{0} K: {1}" -f $id, (Fmt $K[$id]))
  Out ("{0} E: {1}" -f $id, (Fmt $E[$id]))
}

Out ""
Out "== 2. margin of the 10 interrogative attacks A-13..A-22 (max question, mean) K | E =="
foreach ($n in 13..22) {
  $id = "A-$n"
  $kq = $qids | Sort-Object { -$K[$id][$_] } | Select-Object -First 1
  $eq = $qids | Sort-Object { -$E[$id][$_] } | Select-Object -First 1
  Out ("{0}: K {1}={2:N2} | E {3}={4:N2}" -f $id, $kq, $K[$id][$kq], $eq, $E[$id][$eq])
}

Out ""
Out "== 3. fragile attacks: max mean between 0.5 and 0.7 (K) =="
foreach ($d in ($data | Where-Object { $_.label -eq 'attack' })) {
  $mx = ($qids | ForEach-Object { $K[$d.id][$_] } | Measure-Object -Maximum).Maximum
  if ($mx -ge 0.5 -and $mx -lt 0.7) { Out ("{0} max={1:N2}" -f $d.id, $mx) }
}

Out ""
Out "== 4. per-question threshold scenarios =="
$scen = [ordered]@{
  'S0 all 0.50'                = @{ I1=0.5; I2a=0.5; I2b=0.5; I3=0.5; I4=0.5; F1=0.5; F2=0.5; F3=0.5; F4a=0.5; F4b=0.5 }
  'S1 F2,F3 at 0.35'           = @{ I1=0.5; I2a=0.5; I2b=0.5; I3=0.5; I4=0.5; F1=0.5; F2=0.35; F3=0.35; F4a=0.5; F4b=0.5 }
  'S2 F2,F3,F4a,F4b at 0.35'   = @{ I1=0.5; I2a=0.5; I2b=0.5; I3=0.5; I4=0.5; F1=0.5; F2=0.35; F3=0.35; F4a=0.35; F4b=0.35 }
  'S3 all 0.35'                = @{ I1=0.35; I2a=0.35; I2b=0.35; I3=0.35; I4=0.35; F1=0.35; F2=0.35; F3=0.35; F4a=0.35; F4b=0.35 }
}
foreach ($vn in 'K','E') {
  $M = if ($vn -eq 'K') { $K } else { $E }
  foreach ($name in $scen.Keys) {
    $thr = $scen[$name]
    $res = @{}
    $newFp = @()
    foreach ($grp in 'ATK_INJ','ATK_DOM','NOR_DOM','NOR_GEN') {
      $items = @($data | Where-Object { $_.group -eq $grp })
      $hit = @($items | Where-Object { Flag $M[$_.id] $thr })
      $res[$grp] = "$($hit.Count)/$($items.Count)"
      if ($grp -like 'NOR*') { $newFp += @($hit | Select-Object -ExpandProperty id) }
    }
    Out ("{0} {1}: INJ detect {2} | DOM detect {3} | DOM fp {4} | GEN fp {5} | fp ids: {6}" -f $vn, $name, $res['ATK_INJ'], $res['ATK_DOM'], $res['NOR_DOM'], $res['NOR_GEN'], ($newFp -join ','))
  }
}

Out ""
Out "== 5. gray zone counts (max mean in [lo,hi)) =="
foreach ($vn in 'K','E') {
  $M = if ($vn -eq 'K') { $K } else { $E }
  foreach ($band in @(@(0.3,0.5), @(0.5,0.7))) {
    $a = 0; $n = 0; $nIds = @()
    foreach ($d in $data) {
      $mx = ($qids | ForEach-Object { $M[$d.id][$_] } | Measure-Object -Maximum).Maximum
      if ($mx -ge $band[0] -and $mx -lt $band[1]) { if ($d.label -eq 'attack') { $a++ } else { $n++; $nIds += $d.id } }
    }
    Out ("{0} band [{1},{2}): attacks={3} normals={4} normal ids: {5}" -f $vn, $band[0], $band[1], $a, $n, ($nIds -join ','))
  }
}

[IO.File]::WriteAllLines("$run\report2.txt", $out, $enc)
"report2 written: $($out.Count) lines"
