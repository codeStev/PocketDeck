# Privacy

PocketDeck has no account, no analytics, no ads and no crash reporting. There is no server of ours: the app talks to the
services below directly from your phone.

## Stays on your phone
- Your collection (per account), saved decks and settings.
- Your API keys for the AI services, in the app's private storage. They are not included in any Android backup, and the app
  does not back up anything (Android backup is switched off). To move to a new phone, use Export and Import in the app.
- While a scan runs, the app reads the game's screen on your phone to recognise cards. Nothing from the screen is stored or sent;
  only the recognised card counts are kept.

## Downloaded from other services
Each of these sees your phone's IP address and the request itself:
- **Limitless TCG** (`play.limitlesstcg.com`): the recent tournament decklists.
- **TCGdex** (`assets.tcgdex.net`) and **GitHub** (`raw.githubusercontent.com`): card images. Images are kept on the phone after the
  first download.
- **OpenRouter** (`openrouter.ai`): the public model list, only when you open the model picker.
- **Anthropic** (`api.anthropic.com`): the list of Claude models your key can use, only when you open that picker.

## Sent only if you use the AI feature
If you enter an API key and ask the AI for a deck, the app sends your card list, the tournament decks and your optional wish text,
together with your key, to the service you chose (Anthropic or OpenRouter). That service's own privacy policy applies from then on.
**OpenRouter's free models, and the providers behind them, may log what you send.** Do not use the AI feature if you do not want
your card list leaving the phone.

## Auto-scroll
Auto-scroll uses Android's Accessibility service only to swipe the card list in the game while a scan runs. It reads nothing on the
screen and collects nothing. It is off until you agree, and you can switch it off in the system's Accessibility settings at any time.
Automating input in a game may go against that game's terms; you use it at your own risk.

## Contact
Questions and problems: <https://github.com/codeStev/PocketDeck/issues>
