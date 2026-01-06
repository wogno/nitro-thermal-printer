# Guide de test local du package

## Méthode rapide (sans réinstaller)

### ⚡ Méthode recommandée : rsync vers node_modules

La méthode la plus rapide est de synchroniser les fichiers compilés directement dans `node_modules` :

**Depuis le package :**
```bash
yarn sync:erp
```

**Depuis le projet ERP :**
```bash
yarn sync:printer
```

Ce script :
1. Build le package (codegen + build)
2. Synchronise les fichiers vers `node_modules` du projet ERP
3. Plus rapide que `yarn install` (quelques secondes vs plusieurs minutes)

### Alternative : Utiliser `file:` (plus lent)

Le package est configuré avec `file:` dans le `package.json` du projet ERP, mais cela nécessite parfois un rebuild complet.

**Depuis le package :**
```bash
yarn rebuild
```

**Ou utilisez le watch mode :**
```bash
yarn watch
```

### 2. Après modification du code natif (iOS/Android)

Pour les modifications dans `ios/Sources/` ou `android/src/`, vous devez :

**iOS:**
```bash
cd /Users/sandwidimohamed/Documents/projects-software/mes-projets/manage-app/songyamm/apps/erp/ios
pod install
# Puis rebuilder l'app dans Xcode ou avec:
cd ..
yarn ios
```

**Android:**
```bash
cd /Users/sandwidimohamed/Documents/projects-software/mes-projets/manage-app/songyamm/apps/erp
yarn android
```

### 3. Redémarrer le bundler Metro/Expo

Après avoir rebuild le package, redémarrez le bundler :
- Appuyez sur `r` dans le terminal Expo
- Ou arrêtez et relancez `yarn start`

### 4. Scripts disponibles

- `yarn rebuild` - Rebuild complet (codegen + build)
- `yarn watch` - Watch mode automatique (nécessite fswatch)
- `yarn codegen` - Générer uniquement le code Nitro
- `yarn build` - Builder uniquement le TypeScript

## Workflow recommandé

1. Modifier le code dans `nitro-thermal-printer`
2. Lancer `yarn rebuild` (ou `yarn watch` en arrière-plan)
3. Dans le projet ERP, redémarrer le bundler (appuyer sur `r`)
4. Tester les modifications

## Note importante

- Les modifications TypeScript/JavaScript sont prises en compte immédiatement après rebuild
- Les modifications natives nécessitent un rebuild de l'app complète
- Le watch mode nécessite `fswatch` (macOS) : `brew install fswatch`
