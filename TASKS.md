=== Enhancement ===

Ce projet est un vieux projet opensource, il est temps de le mettre à jour et de l'améliorer

Nous avoulons ameliorer les performances avec la nouvelle architecture de react native et aussi migre la base vers kotlin et aussi le meilleur pour ios


### Quelques bugs ou amelioration 
1. Une fois que l'utilisateur quitte l'application ou pour ferme le bluetooth, une fois reallume pour aucune raison la connection a l'imprimante echoue, il faut ferme complement l'application avant que ca refonctionne, ajoute un feature d'auto connection automatique. 

2. Ajoute un feature qui permet de s'avoir si un bluetooth est actuellement connecte isConnected() -> retourne le id ou undifined

3.  Utilise [nitro modules](https://nitro.margelo.com/) pour des performances pres ce que native.

4. isPrinting(): Permet de savoir si l'imprimante est en train d'imprimer ou non

5. Tranforme les differents appeles async native sinon actuellement il y a  bugs qui fais que un printText peut venir avant un autre tuant ainsi la logique lors de l'impression.

6. Permet de mettre en cache les images pour une performance optimal lors de la prochaine impression. Une image n'as pas de delai d'expiration. Et pour les gardera l'URL de l'image comme la cle d'acces, et on pourra mettre en cache jusqu'a max 10 images

7. askPermissions(): On veux ajouter cette feature qui permettra de demander la permission, si deja accepter return true, sinon demande la permission mais si la permission n'est pas accepter on va juste affiche un message un parametres pour aller au niveau des settings
