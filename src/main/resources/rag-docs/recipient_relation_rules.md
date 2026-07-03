# Recipient Relation to Occasion Rules

## Mother Mom Mum Mama Grandmother Grandma Nana
Gifts for maternal figures are the strongest single predictor of MOTHERS_DAY. When the recipient relation is any maternal term AND the order date is within three weeks before Mother's Day (second Sunday of May for US, fourth Sunday of Lent for UK), predict MOTHERS_DAY with high confidence. If the gift message also contains "mom", "mother", or "mother's day", confidence should be very high (above 0.9). BIRTHDAY is the secondary occasion when the message explicitly mentions birthday. CHRISTMAS is possible for November-December orders.

Primary occasion: MOTHERS_DAY. Secondary occasions: BIRTHDAY, CHRISTMAS.

## Father Dad Papa Grandfather Grandpa
Gifts for paternal figures are the strongest single predictor of FATHERS_DAY. When the recipient relation is any paternal term AND the order date is within three weeks before Father's Day (third Sunday of June), predict FATHERS_DAY with high confidence. If the gift message also contains "dad", "father", or "father's day", confidence should be very high (above 0.9). BIRTHDAY is secondary when the message mentions birthday. CHRISTMAS is possible for November-December orders.

Primary occasion: FATHERS_DAY. Secondary occasions: BIRTHDAY, CHRISTMAS.

## Spouse Partner Husband Wife Boyfriend Girlfriend
Gifts for romantic partners indicate either VALENTINES_DAY, ANNIVERSARY, or BIRTHDAY. VALENTINES_DAY is predicted when the order date is between January 25 and February 14. ANNIVERSARY is predicted when the gift message contains "anniversary", "years together", or "milestone". BIRTHDAY is predicted when the message explicitly mentions birthday. When combined with a romantic product (flowers, jewellery, chocolates), the confidence for VALENTINES_DAY or ANNIVERSARY is elevated. Without clear signals, default to BIRTHDAY.

Primary occasions (date-dependent): VALENTINES_DAY (Jan 25-Feb 14), ANNIVERSARY (message signal), BIRTHDAY.

## Friend Best Friend Bestie Colleague Coworker
Gifts for friends and colleagues most commonly signal BIRTHDAY (most gifts between friends are for birthdays). CHRISTMAS is secondary for November-December orders, often representing Secret Santa or office gift exchanges. Without date signals or message keywords, predict BIRTHDAY with moderate confidence. UNKNOWN is appropriate when no signals exist.

Primary occasion: BIRTHDAY. Secondary occasion: CHRISTMAS.

## Son Daughter Child Nephew Niece Grandchild
Gifts for children most commonly signal BIRTHDAY or CHRISTMAS. CHRISTMAS is very strongly indicated for toy or game products in November-December. BIRTHDAY is indicated year-round for children's products. Products like toys, games, and age-themed items combined with a child relation give high confidence.

Primary occasions: BIRTHDAY (year-round), CHRISTMAS (November-December).

## Self Me Myself
Self-purchases most commonly signal BIRTHDAY (treating oneself on one's own birthday) or are an impulse purchase with no specific occasion. Without a gift message or date signal, predict UNKNOWN with low confidence.

Primary occasion: BIRTHDAY. Default: UNKNOWN.

## Teacher Mentor Boss Manager
Gifts for professional relations most commonly signal CHRISTMAS (year-end teacher or office gifts) or BIRTHDAY if the message contains a birthday reference. Without strong signals, default to UNKNOWN.

Primary occasion: CHRISTMAS (November-December), otherwise UNKNOWN.

## Brother Sister Sibling
Gifts for siblings most commonly signal BIRTHDAY or CHRISTMAS. Without stronger date or message signals, predict BIRTHDAY as the default. CHRISTMAS is secondary for November-December orders.

Primary occasion: BIRTHDAY. Secondary occasion: CHRISTMAS.
