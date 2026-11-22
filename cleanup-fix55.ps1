# Supprime les anciens fichiers aux noms francais (renommes en anglais dans le fix55)
$r = "src\main\resources"
$old = @(
 "assets\cosmicisland\blockstates\marmite_posee.json","assets\cosmicisland\blockstates\sac_de_riz.json","assets\cosmicisland\blockstates\feu_de_camp.json",
 "assets\cosmicisland\blockstates\poteau.json","assets\cosmicisland\blockstates\totem.json",
 "assets\cosmicisland\models\item\sac_de_riz.json","assets\cosmicisland\models\item\feu_de_camp.json","assets\cosmicisland\models\item\poteau.json",
 "assets\cosmicisland\models\item\collier.json","assets\cosmicisland\models\item\bandeau.json","assets\cosmicisland\models\item\coquillage_de_riz.json",
 "assets\cosmicisland\models\item\totem.json","assets\cosmicisland\models\item\marmite.json","assets\cosmicisland\models\item\allume_feu.json",
 "assets\cosmicisland\models\item\gourde.json","assets\cosmicisland\models\item\brassard.json",
 "assets\cosmicisland\models\item\marmite_empty.json","assets\cosmicisland\models\item\marmite_water.json","assets\cosmicisland\models\item\marmite_rice.json",
 "assets\cosmicisland\models\item\gourde_water.json","assets\cosmicisland\models\item\gourde_purified.json",
 "data\cosmicisland\loot_tables\blocks\sac_de_riz.json","data\cosmicisland\loot_tables\blocks\feu_de_camp.json",
 "data\cosmicisland\loot_tables\blocks\poteau.json","data\cosmicisland\loot_tables\blocks\totem.json",
 "data\cosmicisland\recipes\feu_de_camp.json","data\cosmicisland\recipes\marmite.json","data\cosmicisland\recipes\allume_feu.json"
)
foreach ($f in $old) { Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $r $f) }
Write-Host "Nettoyage termine."
