# Occasion Prediction Rules

## Birthday
A birthday gift occasion is likely when the gift message contains words like "birthday", "happy birthday", "bday", "born", "turning [age]", or "another year older". The product is often a birthday cake, party supplies, balloons, age-themed item, or personalised gift. The product category may be labelled "Birthday" or "Celebration". The order date is typically within 14 days before the recipient's birthday. Any recipient relation can receive a birthday gift, making the gift message the strongest signal.

Key signals: birthday keyword in gift message, age-related or celebration product, any relation.

## Anniversary
An anniversary gift occasion is likely when the gift message mentions "anniversary", "years together", "years married", "milestone", or "another year with you". The product is commonly jewellery, a romantic experience, a keepsake, or couple-themed item. The recipient relation is most often Spouse, Partner, Husband, or Wife. The order date may fall near a known milestone date or Valentine's Day (February 14).

Key signals: anniversary keyword in message, romantic recipient relation, jewellery or experience product.

## Valentines Day
Valentine's Day is likely when the order date falls between January 25 and February 14. The gift message often contains "valentine", "love", "sweetheart", "be mine", or "roses". The product is roses, chocolates, heart-shaped items, perfume, or jewellery. The recipient relation is Partner, Spouse, Boyfriend, Girlfriend, or Crush.

Key signals: order date in late January or early February, romantic product, romantic recipient relation.

## Mothers Day
Mother's Day is likely when the recipient relation is Mother, Mom, Mum, Mama, Grandmother, Grandma, or Nana. The gift message often contains "mom", "mother", "mum", "mama", or "mother's day". The product is frequently flowers, spa items, jewellery, or sentimental keepsakes. The order date falls within three weeks before Mother's Day, which is the second Sunday of May in the US and the fourth Sunday of Lent (late March) in the UK.

Key signals: maternal recipient relation, order in late April or early May (US) or mid-March (UK).

## Fathers Day
Father's Day is likely when the recipient relation is Father, Dad, Papa, Grandfather, or Grandpa. The gift message often contains "dad", "father", "papa", or "father's day". The product is commonly tools, gadgets, outdoor gear, sporting goods, or spirits. The order date falls within three weeks before Father's Day, which is the third Sunday of June in both the US and UK.

Key signals: paternal recipient relation, order in late May or early June.

## Christmas
Christmas is likely when the order date falls between November 20 and December 25. The gift message mentions "christmas", "xmas", "santa", "holiday season", "season's greetings", or "new year". The product may be wrapped gifts, toys, games, festive decorations, electronics, or clothing. Any recipient relation can receive a Christmas gift.

Key signals: order date in November or December, festive greeting in message, any recipient.

## Thanksgiving
Thanksgiving is likely when the gift message mentions "thanksgiving", "thankful", "grateful", "harvest", or "giving thanks". The order date is within two weeks before Thanksgiving, which falls on the fourth Thursday of November in the US. The product is often a food hamper, wine, table items, or home goods.

Key signals: order date in early to mid-November, food or home product, gratitude message.

## UNKNOWN
Use UNKNOWN when there is insufficient information to determine the occasion, when no strong keyword or date signals are present, when multiple occasions are equally likely with no clear differentiator, or when the gift message is absent and no other signals exist. Set confidence below 0.4 for UNKNOWN predictions.
