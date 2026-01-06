# Problème de Centrage d'Image sur iOS - Documentation

## 🔍 Problème Identifié

L'image imprimée sur iOS n'est pas centrée malgré :
- ✅ Canvas créé avec la largeur exacte de l'imprimante (576px pour 80mm)
- ✅ Image positionnée au centre du canvas (xOffset calculé correctement)
- ✅ Commandes ESC/POS de centrage envoyées (`ESC a 1`)
- ✅ Padding blanc ajouté à gauche de l'image

**Résultat observé** : L'image s'affiche toujours alignée à droite sur le ticket imprimé.

---

## 📚 Sources et Documentation Trouvées

### 1. Problèmes Connus avec SDK d'Impression iOS

**Stack Overflow - Aspect Ratio Issues**
- URL: https://stackoverflow.com/questions/18502428/aspect-ratio-issue-for-image-in-ios-sdk
- **Problème** : Images mal positionnées dans UIImageView avec taille fixe
- **Solution suggérée** : Ajuster dynamiquement la taille de la UIImageView

**Apple Discussions - iOS 17.1.2 Printing Issues**
- URL: https://discussions.apple.com/thread/255343000
- **Problème** : Étiquettes imprimées trop petites et mal alignées après mise à jour iOS
- **Impact** : Problèmes d'alignement après mises à jour iOS

**Address Labels App - Known Issues**
- URL: https://ios.addresslabels.app/known-issues/
- **Problème** : Problèmes d'échelle et d'alignement après iOS 17 et 18
- **Note** : Solution trouvée pour iOS 17, mais problème réapparu avec iOS 18

### 2. Solutions Proposées par la Communauté

**GitHub - react-native-bluetooth-escpos-printer**
- URL: https://github.com/januslo/react-native-bluetooth-escpos-printer/issues/21
- **Problème** : Image décalée vers la droite malgré calculs de centrage corrects
- **Solutions suggérées** :
  1. Utiliser commandes ESC/POS pour centrage
  2. Ajouter marges blanches à gauche
  3. Vérifier paramètres d'alignement du SDK

**StarXpand SDK - Alignment Settings**
- URL: https://www.star-m.jp/products/s_print/sdk/react-native-star-io10/manual/en/ios-swift-api-reference/star-xpand-command/printer/alignment/index.html
- **Solution** : SDK propose paramètres d'alignement (`left`, `center`, `right`)
- **Note** : Vérifier si PrinterSDK supporte ces paramètres

**Zebra MAUI SDK - iOS 18 Delay Issues**
- URL: https://developer.zebra.com/content/long-delay-when-printing-image-ios-18-maui-sdk
- **Problème** : Retards significatifs lors de l'impression d'images sur iOS 18
- **Note** : Problèmes spécifiques à iOS 18

**Brother SDK - Alignment Properties**
- URL: https://support.brother.co.jp/j/s/support/html/mobilesdk/reference/ios_v4/brlmprintimagesettings.html
- **Solution** : Propriétés `hAlignment` et `vAlignment` pour contrôler l'alignement
- **Note** : Vérifier si PrinterSDK a des propriétés similaires

### 3. Approches Techniques Documentées

**Vision Framework d'Apple**
- URL: https://ichi.pro/fr/centrage-de-visage-d-image-ios-a-l-aide-du-cadre-de-vision-d-apple-70315414304203
- **Approche** : Utiliser Vision Framework pour détecter et centrer éléments
- **Note** : Principalement pour visages, mais technique adaptable

**Print to Size - Known Bugs**
- URL: https://support.printtosize.com/known-bugs/
- **Solution** : Envoi direct des travaux d'impression à l'imprimante, contournant le système iOS

---

## 🔧 Solutions Tentées (Sans Succès)

### ✅ Tentative 1 : Canvas avec Padding Blanc
```swift
// Créer canvas de largeur exacte imprimante
// Ajouter padding blanc à gauche
// Positionner image au centre
```
**Résultat** : ❌ SDK ignore le padding blanc

### ✅ Tentative 2 : Commandes ESC/POS
```swift
printerSDK.sendHex("1b6101") // ESC a 1 (center)
printerSDK.printImage(image)
printerSDK.sendHex("1b6100") // ESC a 0 (left)
```
**Résultat** : ❌ Commandes ignorées par `printImage()`

### ✅ Tentative 3 : Image de Largeur Complète
```swift
// Forcer image à largeur exacte imprimante
// Centrer contenu dans cette image
```
**Résultat** : ❌ SDK imprime toujours depuis le bord gauche

---

## 💡 Solutions à Explorer

### Option 1 : Conversion Bitmap + ESC/POS Direct
**Inspiration** : Code Android dans `ImageProcessor.kt`
- Convertir UIImage en bitmap
- Envoyer directement via ESC/POS avec commandes de centrage
- Contourner complètement `printImage()` du SDK

**Avantages** :
- Contrôle total sur le positionnement
- Utilise commandes ESC/POS natives

**Inconvénients** :
- Plus complexe à implémenter
- Nécessite conversion bitmap manuelle

### Option 2 : Vérifier Documentation PrinterSDK
**Actions** :
- Chercher documentation officielle PrinterSDK
- Vérifier si méthode `printImage()` accepte paramètres d'alignement
- Chercher méthode alternative pour centrage

### Option 3 : Créer Image avec Contenu Inversé
**Approche** :
- Si SDK imprime toujours depuis gauche
- Créer image avec padding à droite au lieu de gauche
- Puis retourner horizontalement l'image complète

**Note** : Solution de contournement, pas idéale

### Option 4 : Utiliser `printTextImage()` au lieu de `printImage()`
**Hypothèse** :
- `printTextImage()` pourrait respecter les commandes ESC/POS
- Convertir image en texte/ASCII art
- Envoyer avec commandes de centrage

### Option 5 : Contacter Support PrinterSDK
**Actions** :
- Identifier le SDK exact utilisé (version, source)
- Contacter support technique
- Signaler le bug de centrage
- Demander solution ou workaround

---

## 📦 SDK Identifié

**PrinterSDK utilisé** : SDK binaire propriétaire (`libPrinterSDK.a`)

**Header disponible** : `ios/PrinterSDK/PrinterSDK.h`

**Méthode `printImage:` disponible** :
```objective-c
- (void)printImage:(UIImage*)image;
```

**Observations** :
- ❌ Aucun paramètre d'alignement disponible
- ❌ Méthode ne prend qu'un seul paramètre (UIImage)
- ❌ Pas de documentation publique trouvée
- ❌ SDK binaire (pas de code source)

**Conclusion** : Le SDK semble être un SDK propriétaire fermé, ce qui limite les options de contournement.

---

## 🔍 Prochaines Étapes Recommandées

1. **✅ SDK identifié** : SDK binaire propriétaire, pas de documentation publique

2. **Tester Option 1 (Bitmap + ESC/POS)**
   - Implémenter conversion bitmap
   - Envoyer via `sendHex()` avec commandes ESC/POS
   - Tester si centrage fonctionne

3. **Analyser Code Android**
   - Le code Android utilise `CENTER_ALIGN` avant impression
   - Vérifier si même approche fonctionne sur iOS
   - Adapter si nécessaire

4. **Créer Issue GitHub**
   - Si package open-source, créer issue
   - Documenter le problème avec exemples
   - Demander aide communauté

---

## 📝 Notes Techniques

### Commandes ESC/POS Testées
- `ESC a 1` (0x1B 0x61 0x01) : Centrer
- `ESC a 0` (0x1B 0x61 0x00) : Alignement gauche
- `ESC a 2` (0x1B 0x61 0x02) : Alignement droite

### Dimensions Testées
- Largeur imprimante : 576px (80mm) ou 384px (58mm)
- Image cible : ~230px (40% de 576px)
- xOffset calculé : ~173px (correct mathématiquement)

### Logs Observés
```
[HybridBLEPrinter] Centering image: imageWidth=230.4, printerWidth=576.0, xOffset=172.8
[HybridBLEPrinter] Centered image created: size=576.0x230.4, expected width=576.0
```
**Conclusion** : Le calcul est correct, mais le SDK ignore le centrage.

---

## 🎯 Conclusion

Le problème semble être une **limitation connue du PrinterSDK sur iOS** où la méthode `printImage()` :
- Ignore le padding blanc dans le canvas
- Ignore les commandes ESC/POS de centrage
- Imprime toujours depuis le bord gauche de l'image

**Solution recommandée** : Implémenter Option 1 (Bitmap + ESC/POS direct) pour contourner complètement `printImage()`.
