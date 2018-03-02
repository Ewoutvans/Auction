# Auction
Sponge Auction

This plugin is a global auction plugin that only allows 1 user to auction a item at a time.
It also does not spam the chat because everything happens in the ActionBar

## Commands

Use `/auc` or `/auction`

- `/auc start <price>` start a auction with the item in hand (permission: `auction.start`)
- `/auc cancel` cancel your auction (permission: `auction.cancel`)
- `/auc show` show in a inventory the current item (permission: `auction.show`)
- `/bid [price]` bid on a item if no price is supplied adds 100 to current bid (permission: `auction.bid`)

## Screenshots

![Screenshot 1](images/1.png)
![Screenshot 2](images/2.png)
![Screenshot 3](images/3.png)

## Config
 
The config contains messages you can configure.

```
messages {
    "auction_cancelled" {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="Auction is cancelled"
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auction_failactive" {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="There is already an item being auctioned right now. You have to wait until the auction ends before putting a new item up for auction."
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auction_faildelay" {
        arguments {
            seconds {
                optional=false
            }
        }
        content {
            color=red
            extra=[
                {
                    text="An auction closed recently. Another auction can be started in "
                },
                {
                    color=yellow
                    text="{seconds}"
                },
                {
                    text=" seconds."
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auction_failitem" {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="You must have the item you wish to auction in your hand."
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auction_faillow" {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="Minimum value to start is 0"
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auction_message" {
        arguments {
            item {
                optional=false
            }
            price {
                optional=false
            }
            starter {
                optional=false
            }
            time {
                optional=false
            }
        }
        content {
            color=gold
            extra=[
                {
                    color=green
                    text="{starter}"
                },
                {
                    text=" is auctioning "
                },
                {
                    color=green
                    text="{item}"
                },
                {
                    text=" (info /auc show) price: "
                },
                {
                    color=green
                    text="{price}"
                },
                {
                    text=" timeleft: "
                },
                {
                    color=green
                    text="{time}"
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auction_nobids" {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="No bids - the item has been returned to the owner."
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auction_not_taking_place" {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="No auction is taking place."
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auction_sold" {
        arguments {
            player {
                optional=false
            }
            price {
                optional=false
            }
        }
        content {
            color=green
            extra=[
                {
                    text="Sold! The winning bid was "
                },
                {
                    text="{price}"
                },
                {
                    text=" by "
                },
                {
                    text="{player}"
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auction_success" {
        arguments {}
        content {
            color=green
            extra=[
                {
                    text="Your auction has begun!"
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auctionbid_failbid" {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="You cannot bid on your own auctions."
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auctionbid_failbidder" {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="Bid failed: You are already the highest bidder."
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auctionbid_failmoney" {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="Bid failed: you do not have that much money."
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auctionbid_failtoolow" {
        arguments {
            price {
                optional=false
            }
        }
        content {
            color=red
            extra=[
                {
                    text="Bid failed: That bid is too low. The current high bid is "
                },
                {
                    text="{price}"
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auctionbid_outbid" {
        arguments {
            price {
                optional=false
            }
        }
        content {
            color=red
            extra=[
                {
                    text="You were outbid! The new high bid is "
                },
                {
                    text="{price}"
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "auctionbid_success" {
        arguments {}
        content {
            color=green
            extra=[
                {
                    text="Your bid was accepted! You are the new high bidder."
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    beplayer {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="Be a player"
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "only_cancel_when_starter" {
        arguments {}
        content {
            color=red
            extra=[
                {
                    text="You can only cancel if you are the starter of a auction."
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
    "you_recieved" {
        arguments {
            item {
                optional=false
            }
        }
        content {
            color=green
            extra=[
                {
                    text="You received "
                },
                {
                    text="{item}"
                }
            ]
            text=""
        }
        options {
            closeArg="}"
            openArg="{"
        }
    }
}
```
