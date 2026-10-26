'use strict';
// Исходный многострочный текст владельца. Ссылка — только текст, её не запрашиваем.
module.exports = String.raw`0. скачать актуальную на текущий момент версию пос-а
https://102922.selcdn.ru/pos_installer/99/Setup.iikoCard5.POS.exe

1. удалить старую версию.
 если запросит, то ребутнуться.

2. поставить актуальную версию по ссылке
если запросит, то ребутнуться.

3. остановить службу. если служба не стопится, то переходим дальше:

4. в файле C:\Program Files\iiko\iikoCard5POS\iikoCard5.Pos.Host.exe.config изменить ключ с false на true <add key="ForceHttpSync" value="true" /> находится примерно на 369-й строке после "<!--Force HTTP regardless of the server UseHttpSync setting, without RabbitMQ fallback.-->"

5. запустить службу. если служба не была остановлена в п.3, то ребутнуть машину`;
