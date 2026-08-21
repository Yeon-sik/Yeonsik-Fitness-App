param(
    [string]$OutputPath = (
        Join-Path $PSScriptRoot '..\app\src\main\assets\verified_food_catalog_v3.json'
    )
)

$ErrorActionPreference = 'Stop'

$datasetId = '15100065'
$datasetUrl = 'https://www.data.go.kr/data/15100065/standard.do'
$headerUrl = "https://www.data.go.kr/download/columList.json?pk=$datasetId&ext=JSON"
$header = Invoke-RestMethod -Uri $headerUrl
$columnQuery = ($header.tableVO.colNmList | ForEach-Object {
    'colNmList=' + [uri]::EscapeDataString($_)
}) -join '&'
$rowsUrl = 'https://www.data.go.kr/download/standard.json' +
        "?publicDataPk=$datasetId" +
        "&totalCount=$($header.totalCount)" +
        "&svcTableNm=$($header.tableVO.svcTableNm)" +
        '&perPage=10000&page=1&' + $columnQuery
$responseRows = Invoke-RestMethod -Uri $rowsUrl
$allRows = @($responseRows | ForEach-Object { $_ })
if ($allRows.Count -ne [int]$header.totalCount) {
    throw "Official source returned $($allRows.Count) of $($header.totalCount) rows."
}

# Product policy: one representative official row per searchable food and preparation state.
# Every selected row uses an edible-portion 100 g basis. Raw and grilled rows stay distinct.
$rawCodes = @(
    # Chicken
    'R209-008000501-0000',
    'R209-008000701-0000',
    'R209-008006301-0000',
    'R209-008000601-0000',
    # Beef: Hanwoo, grade 1, raw
    'R209-027068701-0000',
    'R209-027069101-0000',
    'R209-027068301-0000',
    'R209-027068401-0000',
    'R209-027068801-0000',
    'R209-027061601-0000',
    'R209-027061301-0000',
    'R209-027068501-0000',
    'R209-027061801-0000',
    'R209-027068101-0000',
    # Imported beef: chuck eye roll represented by the official U.S. beef chuck row
    'R209-027018401-0000',
    # Pork
    'R209-014008701-0000',
    'R209-014008301-0000',
    'R209-014008401-0000',
    'R209-014001001-0000',
    'R209-014008501-0000',
    'R209-014001201-0000',
    'R209-014008101-0000',
    'R209-014008801-0000',
    # Vegetables and mushrooms
    'R106-003000001-0000',
    'R106-092000001-0000',
    'R106-115000001-0000',
    'R106-129000001-0000',
    'R106-101173701-0000',
    'R106-101033601-0000',
    'R106-148010001-0000',
    'R106-186000001-0000',
    'R106-186010001-0000',
    'R106-194008101-0000',
    'R106-194008001-0000',
    'R106-041007601-0000',
    'R106-132000001-0000',
    'R106-198050001-0000',
    'R107-023000001-0000',
    'R107-025000001-0000',
    'R107-027007101-0000',
    'R106-182000001-0000',
    'R106-112000001-0000',
    'R106-122000001-0000',
    'R106-198040001-0000',
    'R106-030000001-0000',
    'R102-001060001-0000',
    'R102-006000001-0000',
    'R106-053002201-0000',
    'R106-191040001-0000',
    # Fruits
    'R108-050000001-0000',
    'R108-037000001-0000',
    'R108-019010001-0000',
    'R108-010020001-0000',
    'R108-098010001-0000',
    'R108-069000001-0000',
    'R108-092000001-0000',
    # Dry grains used before cooking
    'R101-008000301-0000',
    'R101-008000501-0000',
    'R101-025050101-0000',
    'R101-008000701-0000',
    'R101-047002001-0000',
    # Fish used as the nutrition basis for unseasoned sashimi/raw flesh
    'R211-201174001-0000',
    'R211-059074001-0000',
    'R211-021014001-1208'
)

$grilledCodes = @(
    # Plain grilled fish flesh
    'R211-201174050-0000',
    'R211-059074050-0000',
    'R211-021014050-7300',
    'R211-117014050-0000'
)

$selectedCodes = @($rawCodes + $grilledCodes)

if ($rawCodes.Count -ne 64 -or $grilledCodes.Count -ne 4 -or
        $selectedCodes.Count -ne 68 -or
        ($selectedCodes | Sort-Object -Unique).Count -ne 68) {
    throw 'The verified catalog selection must contain 64 raw and 4 grilled unique codes.'
}

$rowsByCode = @{}
foreach ($row in $allRows) {
    if (-not $rowsByCode.ContainsKey($row.FOOD_CD)) {
        $rowsByCode[$row.FOOD_CD] = @()
    }
    $rowsByCode[$row.FOOD_CD] = @($rowsByCode[$row.FOOD_CD]) + $row
}

$requiredColumns = @('ENERC', 'PROT', 'CHOCDF', 'FATCE', 'NAT', 'FASAT', 'SUGAR')
$allowedMissingColumns = @{
    'R211-201174001-0000' = @('SUGAR')
    'R211-201174050-0000' = @('SUGAR')
    'R211-059074001-0000' = @('SUGAR')
    'R211-059074050-0000' = @('SUGAR')
    'R211-021014001-1208' = @('NAT', 'FASAT')
    'R211-021014050-7300' = @('SUGAR')
    'R211-117014050-0000' = @('SUGAR')
}

function Convert-OfficialNumber {
    param([object]$Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return $null
    }
    return [double]::Parse(
        ([string]$Value).Trim(),
        [System.Globalization.CultureInfo]::InvariantCulture
    )
}

$foods = @()
foreach ($code in $selectedCodes) {
    $matchingRows = @($rowsByCode[$code])
    if ($matchingRows.Count -eq 0) {
        throw "Official source row is missing: $code"
    }
    if ($code -eq 'R211-059074001-0000' -and $matchingRows.Count -gt 1) {
        # This official code is duplicated for belly, back, and representative flesh.
        # The representative FOOD_NM is the only row without a parenthesized sub-cut.
        $matchingRows = @($matchingRows | Where-Object { $_.FOOD_NM -notmatch '\(' })
    }
    if ($matchingRows.Count -ne 1) {
        throw "Official source code is ambiguous ($($matchingRows.Count) rows): $code"
    }
    $row = $matchingRows[0]
    $isGrilled = $grilledCodes -contains $code
    $expectedPreparationCode = if ($isGrilled) { '50' } else { '01' }
    if ($row.DATA_CD -ne 'R' -or $row.FOOD_LV7_CD -ne $expectedPreparationCode) {
        throw "Catalog row preparation does not match selection: $code ($($row.FOOD_NM))"
    }
    if ($row.NUT_CON_SRTR_QUA -ne '100g') {
        throw "Catalog row does not use a 100 g basis: $code"
    }
    foreach ($column in $requiredColumns) {
        $isMissing = [string]::IsNullOrWhiteSpace([string]$row.$column)
        $isAllowedMissing = $allowedMissingColumns.ContainsKey($code) -and
                ($allowedMissingColumns[$code] -contains $column)
        if ($isMissing -and -not $isAllowedMissing) {
            throw "Required nutrient $column is missing for $code"
        }
    }

    $fatGrams = Convert-OfficialNumber $row.FATCE
    $transFatGrams = Convert-OfficialNumber $row.FATRN
    $qualityNotes = @()
    if ($null -ne $transFatGrams -and $transFatGrams -gt $fatGrams) {
        $qualityNotes += 'official_trans_fat_exceeds_total_fat_omitted'
        $transFatGrams = $null
    }

    $food = [ordered]@{
        food_code = $code
        official_name = $row.FOOD_NM
        source_version = $row.CRTR_YMD
        prep_state = if ($isGrilled) { 'cooked' } else { 'raw' }
        cooking_method = if ($isGrilled) { 'grilled' } else { 'raw' }
        calories_kcal = (Convert-OfficialNumber $row.ENERC)
        protein_grams = (Convert-OfficialNumber $row.PROT)
        carbs_grams = (Convert-OfficialNumber $row.CHOCDF)
        fat_grams = $fatGrams
        sodium_mg = (Convert-OfficialNumber $row.NAT)
        saturated_fat_grams = (Convert-OfficialNumber $row.FASAT)
        sugars_grams = (Convert-OfficialNumber $row.SUGAR)
        fiber_grams = (Convert-OfficialNumber $row.FIBTG)
        trans_fat_grams = $transFatGrams
        cholesterol_mg = (Convert-OfficialNumber $row.CHOLE)
        calcium_mg = (Convert-OfficialNumber $row.CA)
        iron_mg = (Convert-OfficialNumber $row.FE)
        phosphorus_mg = (Convert-OfficialNumber $row.P)
        potassium_mg = (Convert-OfficialNumber $row.K)
        vitamin_a_ug_rae = (Convert-OfficialNumber $row.VITA_RAE)
        vitamin_b1_mg = (Convert-OfficialNumber $row.THIA)
        vitamin_b2_mg = (Convert-OfficialNumber $row.RIBF)
        vitamin_b3_mg = (Convert-OfficialNumber $row.NIA)
        vitamin_c_mg = (Convert-OfficialNumber $row.VITC)
        vitamin_d_ug = (Convert-OfficialNumber $row.VITD)
    }
    if ($qualityNotes.Count -gt 0) {
        $food.data_quality_notes = $qualityNotes
    }
    $foods += $food
}

$asset = [ordered]@{
    version = 3
    measurement_policy = 'per_item_preparation_edible_portion_100g'
    source_dataset = 'Korean Integrated Food Nutrition Raw Ingredient Standard Data'
    source_dataset_url = $datasetUrl
    foods = $foods
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$json = $asset | ConvertTo-Json -Depth 8 -Compress
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($resolvedOutput, $json, $utf8WithoutBom)

Write-Output "Generated $($foods.Count) verified foods (64 raw, 4 grilled) at $resolvedOutput"
