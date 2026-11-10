// Turns physical coins into RYSQuests coins: right click the exchanger holding a coin.
// Put this file in kubejs/server_scripts and run /reload.
//
// EXCHANGER is the block. RATES lists the old coins: every `items` of them give `coins`
// of CURRENCY, and what does not make a full batch stays in the hand.

const EXCHANGER = 'minecraft:lodestone'
const CURRENCY = 'coin'
const RATES = {
  'minecraft:gold_nugget': { items: 3, coins: 1 },
  'minecraft:gold_ingot': { items: 1, coins: 3 }
}

BlockEvents.rightClicked(EXCHANGER, event => {
  const id = String(event.item.id)
  const rate = RATES[id]
  if (!rate) return

  // Both hands fire this event; exchangeHeld only ever looks at the main hand.
  const coins = RYSQuestsWallet.exchangeHeld(event.player, id, CURRENCY, rate.items, rate.coins)
  if (coins <= 0) return

  event.player.tell(Text.gold('+' + coins + ' ' + CURRENCY))
  event.cancel()
})
