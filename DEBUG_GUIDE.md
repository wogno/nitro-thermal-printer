# Guide de débogage pour printBulk

## Voir les logs

### iOS (Xcode)

1. **Ouvrir Xcode** et lancer l'app en mode Debug
2. **Ouvrir la console** : `View > Debug Area > Activate Console` (ou `Cmd + Shift + Y`)
3. **Filtrer les logs** : Tapez `[PrintBulk]` dans la barre de recherche de la console
4. **Voir tous les logs** : Les logs commencent par `[PrintBulk]` ou `[BLEPrinter.printBulk]`

### Android (Logcat)

1. **Ouvrir Android Studio** ou utiliser `adb logcat`
2. **Filtrer les logs** :
   ```bash
   adb logcat | grep -i "printbulk\|thermalprinter"
   ```
3. **Ou dans Android Studio** : Ouvrir Logcat et filtrer par tag `PrintBulk`

### React Native (Metro)

Les logs JavaScript apparaissent dans le terminal Metro :
- `[BLEPrinter.printBulk]` - Logs côté JavaScript
- Vérifiez la structure des items avant l'appel natif

## Logs disponibles

### Côté JavaScript (avant l'appel natif)
- Nombre d'items
- Structure de chaque item (quels champs sont présents)

### Côté Swift (dans la fonction native)
- `[PrintBulk] Called with X items` - Confirme l'entrée dans la fonction
- `[PrintBulk] Item X: type=...` - Détails de chaque item
- `[PrintBulk] Processing item X/Y` - Progression du traitement
- `[PrintBulk] ❌ ERROR` - Erreurs détaillées avec stack trace

## Si l'erreur se produit AVANT les logs Swift

Si vous voyez les logs JavaScript mais **PAS** les logs Swift (même pas "Called with X items"), cela signifie que l'erreur se produit lors de la **sérialisation JSI** (conversion JS → Swift).

### Solutions possibles :

1. **Vérifier la structure des données** :
   - Les tableaux doivent être des `Array` JavaScript valides
   - Les nombres doivent être des `number` (pas `string`)
   - Les objets optionnels doivent être `null` ou omis (pas `undefined`)

2. **Vérifier la fonction normalizeBulkItem** :
   - Elle doit omettre les propriétés `undefined`
   - Utiliser `null` pour les valeurs optionnelles vides

3. **Vérifier les types enum** :
   - `PrintBulkItemType.TEXT` doit être un nombre (0, 1, 2, 3)
   - Pas une string comme "TEXT"

## Exemple de logs attendus

```
[BLEPrinter.printBulk] Called with 5 items
[BLEPrinter.printBulk] Item 1: { type: 0, hasContent: true, ... }
========== [PrintBulk] START ==========
[PrintBulk] Called with 5 items
[PrintBulk] Item 1: type=0
  - content: Hello World
  - options: nil
  ...
[PrintBulk] Processing item 1/5, type: 0
[PrintBulk] Completed successfully
```

## Si l'erreur persiste

1. **Copier tous les logs** (JavaScript + Native)
2. **Vérifier la structure exacte** des items dans `thermal.ts`
3. **Tester avec un item simple** :
   ```typescript
   await Printer.printBulk([{
     type: PrintBulkItemType.TEXT,
     content: "Test"
   }]);
   ```
