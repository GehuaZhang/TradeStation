using guosen;
using elsystem;
using tsdata.common;
using tsdata.marketdata; 
using elsystem.collections; 
using elsystem.windows.forms;
using elsystem.io;
using elsystem.drawing;
using elsystem.xml;

vars:OpenFileDialog opendig(null);

vars:  
Dictionary dict_Accounts(null),
Dictionary dict_orders(null),
Dictionary dict_canceled(null),
Dictionary dict_rows(null),
intrabarpersist Dictionary dict_QPs(null); 

vars:
OrdersProvider OP(null),
PositionsProvider PP(null),
AccountsProvider AP(null),
QuotesProvider QP_Vaild(null),
SymbolAttributesProvider SAP_Vaild(null),
intrabarpersist QuotesProvider QP(null)
;

vars:intrabarpersist string dtdate("%Y-%m-%d");
vars:intrabarpersist string dtshort("%Y%m%d%H%M%S"),
intrabarpersist string dtlong("%Y/%m/%d %H:%M:%S"),
intrabarpersist string dtTime("%H:%M:%S"),
intrabarpersist string dtTimeNum("%H%M%S"),
intrabarpersist string dt_hourMinute("%H%M"),
intrabarpersist string prefix("AG"),
intrabarpersist string str_Loss("Loss"),
intrabarpersist string str_profit("Profit"),
intrabarpersist string str_Trailing("Trailing")
;


vars:intrabarpersist string logPath("D:\log.txt");
vars:intrabarpersist string cfgPath("D:\cfg.txt");
vars:string str_root("positions");
vars:string str_child("position");
vars:string str_account("account");
vars:string str_symbol("symbol");
vars:string str_description("description");
vars:string str_quantity("quantity");
vars:string str_stopLoss("stopLoss");
vars:string str_stopProfit("stopProfit");
vars:string str_trailingStop("trailingStop");
vars:string str_percent("percent");
vars:string str_high("highest"); 
vars:string str_vaild("vaild");
vars:string str_monitor("monitor");
vars:string str_State("State");
vars:string str_OrderLoss("Loss");
vars:string str_OrderProfit("Profit");
vars:string str_OrderTrailing("Trailing");
vars:string str_OrderDate("OrderDate");

vars:string OrderState_Sent("已发送");
vars:string OrderState_Queue("队列中");
vars:string OrderState_unSent("未发送");
vars:string OrderState_monitor("监控中");
vars:string OrderState_unknown("");

vars:intrabarpersist OrderTicket otk(null);
vars:string orderName("");

method void AnalysisTechnique_Initialized( elsystem.Object sender, elsystem.InitializedEventArgs args ) 
vars:int loop;
begin
	AssetGuard.Show();
	
	dict_Accounts = new Dictionary;
	dict_orders = new Dictionary;
	dict_canceled = new Dictionary;
	dict_QPs = new Dictionary;
	dict_rows = new Dictionary;
	
	AP = new guosen.AccountsProvider;
	AP.Realtime = TRUE;
	AP.StateChanged += AP_StateChanged;
	AP.Load = TRUE;
	
	OP = new OrdersProvider;
	//OP.StateChanged += OP_StateChanged;
	OP.Updated += OP_Updated;
	OP.Realtime = True;
	
	PP = new PositionsProvider;
	PP.Realtime = True;
	//PP.StateChanged += PP_StateChanged;
	PP.Updated += PP_Updated;	
	
	cb_account.Clear();
	PP.Accounts.Clear();
	
	for loop = 0 to AP.Count-1
	Begin
		if(AP[loop].Type = AccountType.Cash)then
		Begin
			cb_account.AddItem(AP[loop].AccountID);
		End;
	End;
	
	if(cb_account.Count > 0)then
	Begin
		cb_account.SelectedIndex = 0;
	End;
	
end;

Method void setCfgPath(string acct)
Begin
	cfgPath = elsystem.Environment.GetMyWorkDirectory() + "AssetGuard_"+acct+".xml";
	LogPath = elsystem.Environment.GetMyWorkDirectory() + "AssetGuard_log_" + acct + "_" + DateTime.Now.Format("%Y%m%d")+".txt";
End;

Method void OP_Updated(elsystem.Object sender,guosen.OrderUpdatedEventArgs args)
vars:int loop,DataGridViewRow row,bool isMonitor,bool isLong,
string orderID_Loss,string orderID_Profit,string OrderID_TrailingStop,
Order orderLoss,Order orderProfit,order orderTrailingStop;
Begin
	if(args.Reason = OrderUpdateReason.InitialUpdate)then
	Begin
		For loop = 0 to OP.Count-1
		Begin
			if(getOrderName(OP[loop]).Contains(prefix) and OP[loop].OrderID<>"" and dict_orders.Contains(OP[loop].OrderID) = false)then
			Begin
				dict_orders.Add(OP[loop].OrderID.ToString().Trim(),OP[loop]);
			End;
		End;
		
		if(PP.State = DataState.loaded)then
		Begin
			LoadCfg();
			//print("OP_Update:LoadCfg");
		End;
		return;
	End;
	
	//print("State:" , args.Order.State.ToString()," ",args.OrderID," ",getOrderName(args.Order));
	//orderID is empty or order is not app order
	if(args.Order<>null and ( args.Order.OrderID = "" or getOrderName(args.Order).Contains(prefix) = false) )then
	Begin
		Return;
	End; 
	
	if(dict_orders.Contains(args.OrderID) = false)then
	Begin
		dict_orders.Add(args.OrderID,args.Order);
	End;
	
	//row = getRow(args.Order.AccountID,args.Order.Symbol);
	
	row = dict_rows[args.Symbol] astype DataGridViewRow;
	if(row = null)then
	Begin
		return;
	End;
	
	//if the order is stop loss or profit or trailingStop
	if(args.Order.State = OrderState.Queued or args.Order.State = OrderState.Received)then
	Begin
		logg(OrderToString(args.Order));
		row = dict_rows[args.Symbol] astype DataGridViewRow;
		if(row = null)then
		Begin
			return;
		End;
		
		//print("row.Cells[20].Value.ToString()", row.Cells[20].Value.ToString());
		//print("row.Cells[21].Value.ToString()", row.Cells[21].Value.ToString());
		//print("row.Cells[22].Value.ToString()", row.Cells[22].Value.ToString());
	
		isLong = strtobool(row.Cells[17].Value.ToString());
		isMonitor = strtobool(row.Cells[18].Value.ToString());
		
		//if it's long monitor  OR  is not in monitor state
		if(isLong or isMonitor = false)then
		Begin
			args.Order.Cancel();	
		End;
		//if is today monitor and is in monitor
		if(isLong = false and isMonitor)then
		Begin
			if(getOrderName(args.Order).Contains(str_Loss))then
			Begin
				row.Cells[20].Value = args.Order.OrderID;
			End
			Else
			if(getOrderName(args.Order).Contains(str_profit))then
			Begin
				row.Cells[21].Value = args.Order.OrderID;
			End
			Else
			if(getOrderName(args.Order).Contains(str_Trailing))then
			Begin
				row.Cells[22].Value = args.Order.OrderID;
			End;
			saveRowToXML(row);
		End;
	End;
	
	
	if(args.Order.State = OrderState.Received or args.Order.State = OrderState.PartiallyFilled or args.Order.State = OrderState.PartiallyFilledUROut or args.Order.State = OrderState.Canceled or args.Order.State = OrderState.Rejected or args.Order.State = OrderState.Filled)then
	Begin
		row = dict_rows[args.Symbol] astype DataGridViewRow;
		if(row = null)then
		Begin 
			return;
		End;
		
		isLong = strtobool(row.Cells[17].Value.ToString());
		isMonitor = strtobool(row.Cells[18].Value.ToString());
		
		if(isLong = false and isMonitor)then
		Begin
			orderID_Loss = row.Cells[20].Value.ToString();
			orderID_Profit = row.Cells[21].Value.ToString();
			OrderID_TrailingStop = row.Cells[22].Value.ToString();
			
			//orderLoss = getOrderByID(orderID_Loss);
			//orderProfit = getOrderByID(orderID_Profit);
			//orderTrailingStop = getOrderByID(OrderID_TrailingStop);
			
			orderLoss = OP.TryOrder[orderID_Loss];
			orderProfit = OP.TryOrder[orderID_Profit];
			orderTrailingStop = OP.TryOrder[OrderID_TrailingStop];
			
			if(getOrderName(args.Order).Contains(str_Loss))then
			Begin
				if(orderProfit<>null)then
				Begin
					orderProfit.Cancel();
				End;
				if(orderTrailingStop<>null)then
				Begin
					orderTrailingStop.Cancel();
				End;
			End
			Else
			if(getOrderName(args.Order).Contains(str_profit))then
			Begin
				if(orderLoss<>null)then
				Begin
					orderLoss.Cancel();
				End;
				if(orderTrailingStop<>null)then
				Begin
					orderTrailingStop.Cancel();
				End;
			End
			Else
			if(getOrderName(args.Order).Contains(str_Trailing))then
			Begin
				if(orderLoss<>null)then
				Begin
					orderLoss.Cancel();
				End;
				if(orderProfit<>null)then
				Begin
					orderProfit.Cancel();
				End;
			End;
			StopMonitor(row);
		End;
	End;

End;



Method Order getOrderByID(string orderID)
vars:int loop;
Begin
	if(dict_orders = null or dict_orders.Count<=0)then
	Begin
		if(dict_orders.Contains(orderID))then
		Begin
			Return (dict_orders[orderID] astype Order);
		End;
	End;
	if(OP.State = DataState.loaded)then
	Begin
		For loop = 0 to OP.Count-1
		Begin
			if(getOrderName(OP[loop]).Contains(prefix) and OP[loop].OrderID = orderID)then
			Begin
				Return OP[loop];
			End;
		End;
	End;
	Return null;
End;

Method void OP_StateChanged(elsystem.Object sender,tsdata.common.StateChangedEventArgs args)
Begin
	if(OP.State <> DataState.loaded)then
	Begin
		return;
	End;
	
	//TODO
End;


Method void AP_StateChanged(elsystem.Object sender,tsdata.common.StateChangedEventArgs args)
Begin
	if(AP.State <> DataState.loaded)then
	Begin
		return;
	End;
	
	//TODO
End;

Method void PP_StateChanged(elsystem.Object sender,tsdata.common.StateChangedEventArgs args)
Begin
	
	//dg_list.ClearSelection();
End;


Method void QP_Updated(elsystem.Object sender,tsdata.marketdata.QuoteUpdatedEventArgs args)
vars:int loop,QuotesProvider QP_temp,Vector vec,DatagridViewRow row,
string acct,
string sym,
double lastP,
double avail,
double quantity,
double StopLossP,
double StopProfitP,
double TrailingStopP,
double trailingPrice,
bool isPercent,
double highestP,
bool isLong,
bool isMonitor;

Begin
	QP_temp = sender astype QuotesProvider;
	row = dict_rows[QP_temp.Symbol] astype DatagridViewRow;
	//get the row by symbol and ignore if the row is null
	
	if(row = null)then
	Begin
		print("row is null");
		return;
	End;
	
	// update last and marketValue
	row.Cells[7].Value = getLast(QP_temp.Symbol);
	row.Cells[8].Value = getLast(QP_temp.Symbol) * strtonum(dg_list.Rows[loop].Cells[3].Value.ToString());
	
	//print("lastP >= StopProfitP333333333333333: ",lastP >= StopProfitP);
	sym = QP_temp.Symbol;
	lastP = getLast(sym);
	
	acct = row.Cells[1].Value.ToString();
	avail = strtonum(row.Cells[5].Value.ToString());
	quantity = strtonum(row.Cells[11].Value.ToString());
	stopLossP = strtonum(row.Cells[12].Value.ToString());
	stopProfitP = strtonum(row.Cells[13].Value.ToString());
	trailingStopP = strtonum(row.Cells[14].Value.ToString());
	isPercent = strtobool(row.Cells[15].Value.ToString());
	highestP = strtonum(row.Cells[16].Value.ToString());
	isLong = strtobool(row.Cells[17].Value.ToString());
	isMonitor = strtobool(row.Cells[18].Value.ToString());
	
	if(trailingStopP<>0 and highestP = 0 and isLong = TRUE)then
	Begin
		row.Cells[16].Value =  RemoveZero(numtostr(lastP,4));
		highestP = strtonum(row.Cells[16].Value.ToString());
	End
	Else
	if(trailingStopP = 0 or isLong = FALSE)then
	Begin
		row.Cells[16].Value = "";
	End;
	
	//print("lastP >= StopProfitP111111111: ",lastP >= StopProfitP," ",sym," ",avail," ",quantity);
	//if it is in monitor
	if(isMonitor = false)then
	Begin
		return;
	End;
	
	//if it is the today order
	if(isLong = false)then
	Begin
		return;
	End;
	//print("lastP >= StopProfitP2222222: ",lastP >= StopProfitP);
	logg(dgRowToStr(row));
	//if the price is Loss and profit or trailing price trigger
	//StopLoss
	if(StopLossP <> 0 and lastP <= StopLossP)then
	Begin
		otk = new OrderTicket;
		otk.Account = acct;
		otk.Type = tsdata.trading.OrderType.Market;
		otk.Symbol = sym;
		otk.Quantity = minlist(avail,quantity);
		if(otk.Quantity > 0)then
		Begin
			otk.Action = OrderAction.Sell;
			otk.Duration = "aut";
			otk.Send();
			logg("[LONG-ORDER-SENT][STOPLOSS]" + OrdertoString(otk));
			//Stop monitor
		End
		Else
		Begin
			logg("[LONG-ORDER-UNSENT][STOPLOSS] Quantity is less or equal than 0.");	
		End;
		//Stop monitor
		StopMonitor(row);
	End
	Else //StopProfit
	if(StopProfitP <> 0 and lastP >= StopProfitP)then
	Begin
		//print("lastP >= StopProfitP: ",lastP >= StopProfitP);
		otk = new OrderTicket;
		otk.Account = acct;
		otk.Type = tsdata.trading.OrderType.Market;
		otk.Symbol = sym;
		otk.Quantity = minlist(avail,quantity);
		if(otk.Quantity > 0)then
		Begin
			otk.Action = OrderAction.Sell;
			otk.Duration = "aut";
			otk.Send();
			logg("[LONG-ORDER-SENT][STOPPROFIT]" + OrdertoString(otk));
		End
		Else
		Begin
			logg("[LONG-ORDER-UNSENT][STOPPROFIT] Quantity is less or equal than 0.");
		End;
		//Stop monitor
		 StopMonitor(row);
	End;
	
	//trailing stop
	
	
	//calculate the trailing stop price
	if(trailingStopP <> 0)then
	Begin
		if(lastP >= highestP)then
		Begin
			row.Cells[16].Value = RemoveZero(numtostr(lastP,4));
			saveRowToXML(row);
			return;
		End
		Else
		Begin
			if(isPercent)then
			Begin
				trailingPrice = highestP - trailingStopP * highestP / 100;
			End
			Else
			Begin
				trailingPrice = highestP - trailingStopP;
			End;
			if(lastP <= trailingPrice)then
			Begin
				//send order
				otk = new OrderTicket;
				otk.Account = acct;
				otk.Type = tsdata.trading.OrderType.Market;
				otk.Symbol = sym;
				otk.Quantity = minlist(avail,quantity);
				if(otk.Quantity > 0)then
				Begin
					otk.Action = OrderAction.Sell;
					otk.Duration = "aut";
					otk.Send();
					logg("[LONG-ORDER-SENT][TRAILING]" + OrdertoString(otk));
					//row.Cells[19].Value = OrderState_Sent;
					//stopMonitor
				End
				Else
				Begin
					//print("Quantity is less or equal than 0.");
					logg("[LONG-ORDER-UNSENT][TRAILING] Quantity is less or equal than 0.");
				End; // end (otk.Quantity <= 0)
				//Stop monitor
				StopMonitor(row);
			End; // end lastP <= trailingPrice
		End;//end lastP >= highestP
	End; //end trailingStopP <> 0
	
	//save xml
	saveRowToXML(row);
End;

Method Vector getRowsBySymbol(string sym)
vars:int loop,Vector vec;
Begin
	vec = new Vector;
	For loop = 0 to dg_list.Rows.Count
	Begin
		if(dg_list.Rows[loop].Cells[2].Value.ToString().Equals(sym))then
		Begin
			vec.push_back(dg_list.Rows[loop]);
		End;
	End;
	Return vec;
End;

Method DatagridViewRow getRowBySymbol(string sym)
vars:int loop,Vector vec;
Begin
	vec = new Vector;
	For loop = 0 to dg_list.Rows.Count
	Begin
		if(dg_list.Rows[loop].Cells[2].Value.ToString().Equals(sym))then
		Begin
			return dg_list.Rows[loop];
		End;
	End;
	Return null;
End;

Method void PP_Updated(elsystem.Object sender,guosen.PositionUpdatedEventArgs args)
vars:DataGridViewRow row;
Begin
	if(args.Reason = tsdata.trading.PositionUpdateReason.InitialUpdate)then
	Begin
		LoadGrid();
		return;
	End;
	//print(args.Reason.ToString()," " ,args.AccountID," ",args.Symbol);
	
	//args.Position is not null 
	if(args.Reason = tsdata.trading.PositionUpdateReason.Added)then
	Begin
		if(getRow(args.AccountID,args.Symbol) = null)then
		Begin
			addPosition(args.Position);
		End;
	End;
	//Position is delete
	if(args.Reason = tsdata.trading.PositionUpdateReason.Removed)then
	Begin
		removeRow(args.AccountID,args.Symbol);
		return;
	End;
	
	//RealUpdated
	if(args.Reason = tsdata.trading.PositionUpdateReason.RealtimeUpdate)then
	Begin
		row = dict_rows[args.Symbol] astype DataGridViewRow;
		if(row = null)then
		Begin
			addPosition(args.Position);	
		End
		Else
		Begin
			UpdatePosition(row,args.Position);
		End;
	End;
End;

Method void removeRow(string acct,string sym)
vars:int loop;
Begin
	For loop = 0 to dg_list.Rows.Count
	Begin
		if(dg_list.Rows[loop].Cells[1].Value.ToString().Equals(acct) and dg_list.Rows[loop].Cells[2].Value.ToString().Equals(sym))then
		Begin
			dg_list.Rows.RemoveAt(loop);
			removeNode(acct,sym);
			return;
		End;
	End;
End;

Method void removeNode(string acct,string sym)
vars:XmlDocument doc,XmlElement root,XmlNode xnode,int loop,int ct,string account,string symStr;
Begin
	doc = LoadDoc();
	root = doc.DocumentElement;
	ct = root.ChildNodes.Count;
	
	for loop = ct-1 downto 0
	Begin
		account = getField(root.ChildNodes[loop] , str_account);
		symStr =  getField(root.ChildNodes[loop] , str_symbol);
		if(account = acct and symStr = sym)then
		Begin
			root.RemoveChild(root.ChildNodes[loop]);
		End;
	End;
	doc.Save(cfgPath);
End;

Method void LoadGrid()
vars:int loop;
Begin
	if(PP.State <> DataState.loaded) then
	Begin
		return;
	End;
	dg_list.Rows.Clear();
	
	if(OP.State = DataState.loaded)then
	Begin
		LoadCfg();
	End;
End;

Method void LoadCfg()
vars:int loop,XmlDocument doc,XmlElement root,XmlNode xnode,int ct,string account,string sym;
Begin
	For loop = 0 to PP.Count-1
	Begin
		addPosition(PP[loop]);
	End;
	
	doc = LoadDoc();
	root = doc.DocumentElement;
	ct = root.ChildNodes.Count;
	
	for loop = ct-1 downto 0
	Begin
		account = getField(root.ChildNodes[loop] , str_account);
		sym =  getField(root.ChildNodes[loop] , str_symbol);
		if(positionExist(account,sym) = false)then
		Begin
			root.RemoveChild(root.ChildNodes[loop]);
		End;
	End;
	doc.Save(cfgPath);
	
End;

Method bool positionExist(string acct,string sym)
Begin
	if(PP.State <> DataState.loaded)then
	Begin
		Return TRUE;
	End
	Else
	Begin
		if(PP.TryPosition[sym,acct] = null)then
		Begin
			Return FALSE;
		End
		Else
		Begin
			Return TRUE;
		End;
	End;
End;

Method void addPosition(Position posi)
vars:DataGridViewRow newRow,XMLNode node,QuotesProvider QP_temp,string sentRS;
Begin
	newrow = DataGridViewRow.Create("");
	newrow.Resizable = DataGridViewTriState.False;
	if(dict_rows.Contains(posi.Symbol) = false)then
	Begin
		dg_list.Rows.Insert(0,newRow);
		dict_rows.Add(posi.Symbol,newrow);
	End
	Else
		return; 
	newrow.Cells[0].Value = FALSE;	
	newrow.Cells[1].Value = posi.AccountID;
	newrow.Cells[2].Value = posi.Symbol;
	newrow.Cells[3].Value = posi.Description;
	newrow.Cells[4].Value = posi.Quantity;
	newrow.Cells[5].Value = posi.QuantityAvailable;
	newrow.Cells[6].Value = RemoveZero(numtostr(Round(posi.AveragePrice,2),2));
	newrow.Cells[7].Value = ""; //last
	newrow.Cells[8].Value = "";//market Value
	newrow.Cells[9].Value = RemoveZero(numtostr(Round(posi.OpenPL,2),2));
	newrow.Cells[10].Value = RemoveZero(numtostr(Round(posi.PLPerQuantity,2),2)); 
	newrow.Cells[11].Value = ""; //quantity
	newrow.Cells[12].Value = ""; //last stop loss
	newrow.Cells[13].Value = ""; //last stop profit
	newrow.Cells[14].Value = ""; // trailing stop loss
	newrow.Cells[15].Value = FALSE;
	newrow.Cells[16].Value = ""; // highest
	newrow.Cells[17].Value = FALSE; // vaild
	newrow.Cells[18].Value = FALSE;
	newrow.Cells[19].Value = ""; // state
	newrow.Cells[20].Value = ""; // loss order ID
	newrow.Cells[21].Value = ""; // profit order ID
	newrow.Cells[22].Value = ""; // trailing order ID
	newrow.Cells[23].Value = ""; // date
	
	
	newrow.Cells[0].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[1].Font = elsystem.drawing.Font.Create("Consolas", 9.00, 0);
	newrow.Cells[2].Font = elsystem.drawing.Font.Create("Consolas", 9.00, 0);
	newrow.Cells[3].Font = elsystem.drawing.Font.Create("微软雅黑", 9.00, 0);
	newrow.Cells[4].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[5].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[6].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[7].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[8].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[9].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[10].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[11].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[12].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[13].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[14].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[15].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[16].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[17].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[18].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[19].Font = elsystem.drawing.Font.Create("微软雅黑", 9.00, 0);
	newrow.Cells[20].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[21].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[22].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);
	newrow.Cells[23].Font = elsystem.drawing.Font.Create("Consolas", 9.50, 0);

	
	
	node = getNode(posi.AccountID , posi.Symbol);
	if(node <> null)then
	Begin
		newrow.Cells[11].Value = getField(node,str_quantity); //quantity
		newrow.Cells[12].Value = getField(node,str_stopLoss); //last stop loss
		newrow.Cells[13].Value = getField(node,str_stopProfit); //last stop profit
		newrow.Cells[14].Value = getField(node,str_trailingStop); // trailing stop loss
		newrow.Cells[15].Value = strtobool(getField(node,str_percent)); // mov stop loss
		newrow.Cells[16].Value = getField(node,str_high); // highest
		if(strtobool(getField(node,str_vaild)) = false and strtobool(getField(node,str_monitor)) )then // day vaild
		Begin
			if(getField(node,str_OrderDate) <> DateTime.Now.Format(dtdate))then
			Begin
				//outof date, reset the setting
				newrow.Cells[17].Value = strtobool(getField(node,str_vaild)); // vaild
				newrow.Cells[18].Value = FALSE; // monitor
				newrow.Cells[19].Value = ""; // state
				newrow.Cells[20].Value = ""; // loss order ID
				newrow.Cells[21].Value = ""; // profit order ID
				newrow.Cells[22].Value = ""; // trailing order ID
				newrow.Cells[23].Value = ""; // date
			End
			Else //today
			Begin
				newrow.Cells[17].Value = strtobool(getField(node,str_vaild)); // vaild
				//find three OrderID and if the state is filled or acitve to adjust 17 and 18 19 20 21 22
				//waitting
				//print(posi.Symbol," GetOrdersRs:",getOrdersRs(getField(node,str_OrderLoss),getField(node,str_OrderProfit),getField(node,str_OrderTrailing)));
				sentRS = getOrdersRs(getField(node,str_OrderLoss),getField(node,str_OrderProfit),getField(node,str_OrderTrailing));
				if(sentRS = OrderState_Queue)then //reset
				Begin
					newrow.Cells[17].Value = strtobool(getField(node,str_vaild)); // vaild
					newrow.Cells[18].Value = TRUE; // monitor
					newrow.Cells[19].Value = OrderState_Queue; // state
					newrow.Cells[20].Value = getField(node,str_OrderLoss); // loss order ID
					newrow.Cells[21].Value = getField(node,str_OrderProfit); // profit order ID
					newrow.Cells[22].Value = getField(node,str_OrderTrailing); // trailing order ID
					newrow.Cells[23].Value = getField(node,str_OrderDate); // date
				End
				Else
				if(sentRS = OrderState_Sent)then //reset
				Begin
					newrow.Cells[17].Value = strtobool(getField(node,str_vaild)); // vaild
					newrow.Cells[18].Value = FALSE; // monitor
					newrow.Cells[19].Value = OrderState_Sent; // state
					newrow.Cells[20].Value = ""; // loss order ID
					newrow.Cells[21].Value = ""; // profit order ID
					newrow.Cells[22].Value = ""; // trailing order ID
					newrow.Cells[23].Value = ""; // date
				End
				Else
				if(sentRS = OrderState_Unsent)then //reset
				Begin
					newrow.Cells[17].Value = strtobool(getField(node,str_vaild)); // vaild
					newrow.Cells[18].Value = strtobool(getField(node,str_vaild)); // monitor
					newrow.Cells[19].Value = OrderState_Sent; // state
					newrow.Cells[20].Value = ""; // loss order ID
					newrow.Cells[21].Value = ""; // profit order ID
					newrow.Cells[22].Value = ""; // trailing order ID
					newrow.Cells[23].Value = ""; // date
				End
				Else //unknown state 
				Begin
					newrow.Cells[17].Value = strtobool(getField(node,str_vaild)); // vaild
					newrow.Cells[18].Value = FALSE; // monitor
					newrow.Cells[19].Value = getField(node,str_State); // state
					newrow.Cells[20].Value = getField(node,str_OrderLoss); // loss order ID
					newrow.Cells[21].Value = getField(node,str_OrderProfit); // profit order ID
					newrow.Cells[22].Value = getField(node,str_OrderTrailing); // trailing order ID
					newrow.Cells[23].Value = getField(node,str_OrderDate); // date
				End;
			End;
		End
		Else// long vaild
		Begin
			newrow.Cells[17].Value = strtobool(getField(node,str_vaild)); // vaild
			newrow.Cells[18].Value = strtobool(getField(node,str_monitor)); // vaild
			newrow.Cells[19].Value = ""; // state
			newrow.Cells[20].Value = ""; // loss order ID
			newrow.Cells[21].Value = ""; // profit order ID
			newrow.Cells[22].Value = ""; // trailing order ID
			newrow.Cells[23].Value = ""; // date
		End;
		
		//write the row to xml
		saveRowToXML(newrow);
	End;
	
	if(dict_QPs.Contains(posi.Symbol.ToUpper()))then
	Begin
		QP_temp = dict_QPs[posi.Symbol.ToUpper()] astype QuotesProvider;
		QP_temp.Load = false;
		QP_temp.Load = true;
	End
	Else
	Begin
		QP = new QuotesProvider;
		QP.Symbol = posi.Symbol.ToUpper();
		QP.Fields += QuoteFields.Last;
		QP.Fields += QuoteFields.Description;
		QP.Updated += QP_Updated;
		dict_QPs.Add(posi.Symbol.ToUpper(),QP);
		QP.Load = TRUE;
	End;	
End;

//need to fix
Method string getOrdersRs(string orderLossID,string orderProfitID, string orderTrailingID)
vars:int loop,Order odLoss,Order odProfit,Order odTrailing;
Begin
	//print("OP.State：",OP.State.ToString());
	//print("orderLossID：", orderLossID);
	//print("orderProfitID：", orderProfitID);
	//print("orderTrailingID：", orderTrailingID);

	if(OP.State = DataState.loaded)then
	Begin
		Try
			odLoss = OP.TryOrder[orderLossID];
			odProfit = OP.TryOrder[orderProfitID];
			odTrailing = OP.TryOrder[orderTrailingID];
			//print("odTrailing = null:",odTrailing=null);
			//one not null
			if(odLoss <> null and odProfit = null and odTrailing = null)then
			Begin
				Return getOrdersRs(odLoss.OrderID);
			End
			Else
			if(odLoss = null and odProfit <> null and odTrailing = null)then
			Begin
				Return getOrdersRs(odProfit.OrderID);
			End
			Else
			if(odLoss = null and odProfit = null and odTrailing <> null)then
			Begin
				Return getOrdersRs(odTrailing.OrderID);
			End;
			
			//two not null
			if(odLoss <> null and odProfit <> null and odTrailing = null)then
			Begin
				Return getOrdersRs(odLoss.OrderID , odProfit.OrderID);
			End
			Else
			if(odLoss = null and odProfit <> null and odTrailing <> null)then
			Begin
				Return getOrdersRs(odProfit.OrderID , odTrailing.OrderID);
			End
			Else
			if(odLoss <> null and odProfit = null and odTrailing <> null)then
			Begin
				Return getOrdersRs(odLoss.OrderID , odTrailing.OrderID);
			End;
			
			
			//all not null
			if(odLoss<>null and odProfit<>null and odTrailing<>null)then
			Begin
				if(odLoss.State = OrderState.Queued and odProfit.State = OrderState.Queued and odTrailing.State = OrderState.Queued)then
				Begin
					Return OrderState_Queue;
				End;
				
				if(isOrderSent(odLoss.State))then
				Begin
					odProfit.Cancel();
					odTrailing.Cancel();
					Return OrderState_Sent;
				End
				Else
				if(isOrderSent(odProfit.State))then
				Begin
					odLoss.Cancel();
					odTrailing.Cancel();
					Return OrderState_Sent;
				End
				Else
				if(isOrderSent(odTrailing.State))then
				Begin
					odLoss.Cancel();
					odProfit.Cancel();
					Return OrderState_Sent;
				End
				Else//all order is not sent
				Begin
					Return OrderState_unSent;
				End;
			End;
			
		Catch(Exception ex)
			//print("[getOrdersRs]:",ex.Message," OrderCount:",dict_orders.Count);
			Return OrderState_unknown;
		End;
	End
	Else
		Return OrderState_unknown;
End;

Method string getOrdersRs(string orderID1,string orderID2)
vars:Order od1,Order od2;
Begin
	if(OP.State = DataState.loaded)then
	Begin
		Try
			od1 = OP.TryOrder[orderID1];
			od2 = OP.TryOrder[orderID2];
			if(od1.State = OrderState.Queued and od2.State = OrderState.Queued)then
			Begin
				Return OrderState_Queue;
			End;
			
			if(isOrderSent(od1.State))then
			Begin
				od2.Cancel();
				Return OrderState_Sent;
			End
			Else
			if(isOrderSent(od2.State))then
			Begin
				od1.Cancel();
				Return OrderState_Sent;
			End
			Else//all order is not sent
			Begin
				Return OrderState_unSent;
			End;
		Catch(Exception ex)
			Return OrderState_unknown;
		End;
	End
	Else
		Return OrderState_unknown;
End;

Method string getOrdersRs(string orderID)
vars:Order od1;
Begin
	if(OP.State = DataState.loaded)then
	Begin
		Try
			od1 = OP.TryOrder[orderID];
			if(od1.State = OrderState.Queued)then
			Begin
				Return OrderState_Queue;
			End;
			
			if(isOrderSent(od1.State))then
			Begin
				Return OrderState_Sent;
			End
			Else//all order is not sent
			Begin
				Return OrderState_unSent;
			End;
		Catch(Exception ex)
			Return OrderState_unknown;
		End;
	End
	Else
		Return OrderState_unknown;
End;

Method bool isOrderSent(OrderState osd)
Begin
	if(osd = OrderState.Received or osd = OrderState.PartiallyFilled or osd = OrderState.PartiallyFilledUROut or osd = OrderState.Filled or osd = OrderState.Rejected or osd = OrderState.Canceled)then
	Begin
		Return true;
	End;
	Return false;
End;

Method void UpdatePosition(DataGridViewRow row,Position posi)
Begin
	row.Cells[3].Value = posi.Description;
	row.Cells[4].Value = posi.Quantity;
	row.Cells[5].Value = posi.QuantityAvailable;
	row.Cells[6].Value = RemoveZero(numtostr(Round(posi.AveragePrice,2),2));
	row.Cells[9].Value = RemoveZero(numtostr(Round(posi.OpenPL,2),2));
	row.Cells[10].Value = RemoveZero(numtostr(Round(posi.PLPerQuantity,2),2)); 
End;

Method DataGridViewRow getRow(string acct,string sym)
vars:int loop;
Begin
	For loop = 0 to dg_list.Rows.Count
	Begin
		if(dg_list.Rows[loop].Cells[1].Value.ToString().Equals(acct) and dg_list.Rows[loop].Cells[2].Value.ToString().Equals(sym))then
		Begin
			Return dg_list.Rows[loop];
		End;
	End;
	Return null;
End;

Method string getOrderName(Order ord) 
vars:string od_name;
Begin
	od_name = "";
	Try
		od_name = ord.ExtendedProperties["OrderName"].ToString();
	catch(elsystem.Exception ex)
	End;
	Return od_name;
End;







Method bool isExistNode(string account,string sym)
Begin
	if(getNode(account,sym) = null)then
	Begin
		Return false;
	End
	Else
		Return true;
End;

Method void addPosition(DataGridViewRow row)
vars:XmlDocument doc,XmlElement root,XmlElement optionNode,string account, string sym, string qty, string stopLoss, string stopProfit, string trailingStop,bool percent,string vaild,bool monitor;
Begin
	doc = LoadDoc();
	root = doc.DocumentElement;
	optionNode = AddChild(doc, root, str_child, "");
	AddChild(doc, optionNode, str_account, row.Cells[1].Value.ToString());
	AddChild(doc, optionNode, str_symbol, row.Cells[2].Value.ToString());
	AddChild(doc, optionNode, str_description, row.Cells[3].Value.ToString());
	AddChild(doc, optionNode, str_quantity, row.Cells[4].Value.ToString());
	AddChild(doc, optionNode, str_stopLoss, row.Cells[12].Value.ToString());
	AddChild(doc, optionNode, str_stopProfit, row.Cells[13].Value.ToString());
	AddChild(doc, optionNode, str_trailingStop, row.Cells[14].Value.ToString());
	AddChild(doc, optionNode, str_percent, row.Cells[15].Value.ToString());
	AddChild(doc, optionNode, str_high, row.Cells[16].Value.ToString());
	AddChild(doc, optionNode, str_vaild, row.Cells[17].Value.ToString());
	AddChild(doc, optionNode, str_monitor, row.Cells[18].Value.ToString());
	doc.Save(cfgPath);
End;

Method XmlDocument LoadDoc()
vars:XmlDocument doc;
Begin
	doc = XmlDocument.Create();
	Try
		doc.Load(cfgPath);
	Catch(elsystem.io.FileNotFoundException ex)
		createXML();
		doc.Load(cfgPath);
	Catch(elsystem.Exception ex1)
		//print("error"); 
		Filedelete(cfgPath);
	End;
	Return doc;
End;

Method void createXML()
vars:XmlDocument doc,XmlElement root;
Begin
	doc = XmlDocument.Create();
	root = doc.CreateElement(str_root);
	doc.AppendChild(root);
	doc.Save(cfgPath);
End;

method XmlElement AddChild(XmlDocument doc, XmlElement parent, string name, string value)
vars:
   XmlElement child;
begin
   child = doc.CreateElement(name);
   child.InnerText = value;
   parent.AppendChild(child);
   return child;
end;

Method XmlNode getNode(string account, string sym)
vars:XmlDocument doc,XmlElement root,int loop,int ct,string act,string sy;
Begin
	doc = LoadDoc();
	root = doc.DocumentElement;
	ct = root.ChildNodes.Count;
	for loop = 0 to ct-1
	Begin
		act = getField(root.ChildNodes[loop] , str_account);
		sy =  getField(root.ChildNodes[loop] , str_symbol);
		if(act = account and sym = sy)then
		Begin
			Return root.ChildNodes[loop];
		End;
	End;
	Return null;
End;

Method void updateXMLNode(string acct,string sym,DataGridViewRow row)
vars:XmlDocument doc,XmlElement root,XmlElement xnode,int loop,int ct,string account,string symStr,bool islong,bool isMonitor,string OrderLossID,string OrderProfitID,string OrderTrailingID;
Begin
	if(row = null)then
	Begin
		return;
	End;
	setRowColor(row);
	doc = LoadDoc();
	root = doc.DocumentElement;
	ct = root.ChildNodes.Count;
	
	for loop = 0 to ct-1
	Begin
		account = getField(root.ChildNodes[loop] , str_account);
		symStr =  getField(root.ChildNodes[loop] , str_symbol);
		if(account = acct and symStr = sym)then
		Begin
			islong = (row.Cells[17].Value) astype bool;
			isMonitor = row.Cells[18].Value astype bool;
			
			xnode = root.ChildNodes[loop] astype XmlElement;
			setNode(doc ,xnode , str_description , row.Cells[3].Value.ToString() );
			setNode(doc ,xnode , str_quantity , row.Cells[11].Value.ToString() );
			setNode(doc ,xnode , str_stopLoss , row.Cells[12].Value.ToString() );
			setNode(doc ,xnode , str_stopProfit , row.Cells[13].Value.ToString() );
			setNode(doc ,xnode , str_trailingStop , row.Cells[14].Value.ToString() );
			setNode(doc ,xnode , str_percent , row.Cells[15].Value.ToString() );
			setNode(doc ,xnode , str_high , row.Cells[16].Value.ToString() );
			setNode(doc ,xnode , str_vaild , row.Cells[17].Value.ToString() );
			setNode(doc ,xnode , str_monitor , row.Cells[18].Value.ToString() );
			if(islong and isMonitor)then
			Begin
				row.Cells[19].Value = OrderState_monitor;
			End
			Else
			Begin
				OrderLossID = row.Cells[20].Value.ToString();
				OrderProfitID = row.Cells[21].Value.ToString();
				OrderTrailingID = row.Cells[22].Value.ToString();
			
				row.Cells[19].Value = getOrdersRs( OrderLossID , OrderProfitID, OrderTrailingID );
			End;
			
			setNode(doc ,xnode , str_State , row.Cells[19].Value.ToString() );
			setNode(doc ,xnode , str_OrderLoss , OrderLossID );
			setNode(doc ,xnode , str_OrderProfit , OrderProfitID );
			setNode(doc ,xnode , str_OrderTrailing , OrderTrailingID );
			setNode(doc ,xnode , str_OrderDate , row.Cells[23].Value.ToString() );
			doc.Save(cfgPath);
		End;
	End;
End;

Method void setRowColor(DataGridViewRow row)
vars:int loop,bool islong,bool isMonitor;
Begin
	islong = (row.Cells[17].Value) astype bool;
	isMonitor = row.Cells[18].Value astype bool;
	
	For loop = 0 to dg_list.ColumnCount-1
	Begin
		row.Cells[loop].BackColor = Color.Empty;
	End;
	 
	if(isMonitor)then
	Begin
		if(islong)then 
		Begin
			For loop = 0 to dg_list.ColumnCount-1
			Begin
				row.Cells[loop].BackColor = Color.IndianRed;
			End;
		End
		Else
		Begin
			For loop = 0 to dg_list.ColumnCount-1
			Begin
				row.Cells[loop].BackColor = Color.Cornsilk;
			End;
		End;
	End;	
End;

Method void setNode(XmlDocument doc,XmlElement parent,string field,string value)
Begin
	if(parent.Item[field] = null)then
	Begin
		AddChild(doc, parent, field, value);
	End
	Else
	Begin
		parent.Item[field].InnerText = value;
	End;	
End;

Method string getField(XmlNode node,string field)
vars:string rtValue;
Begin
	rtValue = "";
	Try
		rtValue = node.Item[field].InnerText.ToUpper();
	Catch(elsystem.Exception ex)
		rtValue = "";
	End;
	Return rtValue;
End;

Method string getField(string account,string sym, string field)
vars:XmlDocument doc,XmlElement root,XmlNode rootNode;
Begin
	doc = LoadDoc();
	//print(doc.DocumentType.ToString());
	Return "";
End;

Method bool strToBool(string str)
Begin
	if(str.ToLower().Equals("true"))then
	Begin
		Return true;
	End
	Else
		Return false;
End;

Method String OrderStateParse(OrderState odState)
Begin
	Switch(odState)
	Begin
		Case OrderState.Canceled:
			Return "已取消";
		Case OrderState.Expired:
			Return "已过期";
		Case OrderState.Filled:
			Return "完全成交";
		Case OrderState.PartiallyFilled:
			Return "部分成交";
		Case OrderState.PartiallyFilledUROut:
			Return "部成撤单";
		Case OrderState.Queued:
			Return "队列中";
		Case OrderState.Received:
			Return "已接收";
		Case OrderState.Rejected:
			Return "已拒绝";
		Case OrderState.SendFailed:
			Return "发送失败";
		Case OrderState.Sending:
			Return "发送中";
		Case OrderState.Unsent:
			Return "未发送";
		Default:
			Return odState.ToString();
	End;
End;

Method String OrderTypeParse(tsdata.trading.OrderType odType)
Begin
	Switch(odType)
	Begin
		Case tsdata.trading.OrderType.Market:
			Return "市价";
		Case tsdata.trading.OrderType.Limit:
			Return "限价";
		Case tsdata.trading.OrderType.Stoplimit:
			Return "限价止损";
		Case tsdata.trading.OrderType.Stopmarket:
			Return "市价止损";
		Case tsdata.trading.OrderType.unknown:
			Return "未知";
		Default:
			Return odType.ToString();
	End;
End;

Method string dgRowToStr(DataGridViewRow dgr_temp)
vars:int loop,Vector vec_temp,int colCount;
Begin
	colCount = dg_list.ColumnCount;
	vec_temp = new Vector;
	for loop = 0 to colCount-1
	Begin
		vec_temp.push_back(dgr_temp.Cells[loop].Value.ToString());
	End;
	Return elstring.Join(",",vec_temp,0,vec_temp.Count);
End;

Method String OrderActionParse(OrderAction odAction)
Begin
	Switch(odAction)
	Begin
		Case OrderAction.BorrowToBuy:
			Return "融资买入";
		Case OrderAction.BorrowToSell:
			Return "融券卖出";
		Case OrderAction.Buy:
			Return "买入";
		Case OrderAction.BuyToClose:
			Return "平空仓";
		Case OrderAction.BuyToOpen:
			Return "开多仓";
		Case OrderAction.BuyToPay:
			Return "买券还券";
		Case OrderAction.CollateralBuy:
			Return "担保品买入";
		Case OrderAction.CollateralSell:
			Return "担保品卖出";
		Case OrderAction.ETFPurchase:
			Return "ETF申购";
		Case OrderAction.PayByCash:
			Return "直接还款";
		Case OrderAction.PayByStock:
			Return "直接还券";
		Case OrderAction.Sell:
			Return "卖出";
		Case OrderAction.SellShort:
			Return "开空仓";
		Case OrderAction.SellToClose:
			Return "平多仓";
		Case OrderAction.SellToOpen:
			Return "开空仓";
		Case OrderAction.SellToPay:
			Return "卖券还款";
		Default:
			Return odAction.ToString();
	End;
End;

Method Color OrderStateColor(OrderState ost)
Begin
	switch(ost)
	Begin
		Case OrderState.Canceled:
			Return Color.Yellow;
		Case OrderState.PartiallyFilled:
			Return Color.RoyalBlue;
		Case OrderState.PartiallyFilledUROut:
			Return Color.Aqua;
		Case OrderState.Received:
			Return SystemColors.Control;
		Case OrderState.Filled:
			Return Color.LightBlue;
		Case OrderState.Rejected:
			Return Color.Magenta;
		default:
			Return SystemColors.Control;
	End;
End;

Method string getOrderName(OrderTicket otk)
vars:string od_name;
Begin
	od_name = "";
	Try
		od_name = otk.ExtendedProperties["OrderName"].ToString();
	catch(elsystem.Exception ex)
	End; 
	Return od_name;
End;

//Join the properties of order object to a string
Method string OrderToString(Order ord)
vars:Vector vec_tmp,string sep,string sepChar;
Begin
	sep = ",";
	sepChar = "";
	vec_tmp = new Vector;
	vec_tmp.Push_back("" + sepChar + ord.OrderID.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + ord.AccountID);
	vec_tmp.Push_back("" + sepChar + ord.Action.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + ord.EnteredQuantity.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + ord.EnteredTime.Format(dtlong)); 
	vec_tmp.Push_back("" + sepChar + ord.FilledQuantity.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + ord.FilledTime.Format(dtshort));
	vec_tmp.Push_back("" + sepChar + numtostr(ord.LimitPrice,3));
	vec_tmp.Push_back("" + sepChar + ord.State.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + ord.StateDetail.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + ord.Symbol.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + ord.Type.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + getOrderName(ord));
	
	Return elstring.Join(",",vec_tmp,0,vec_tmp.Count); 
End;

Method string OrderToString(OrderTicket otk)
vars:Vector vec_tmp,string sep,string sepChar;
Begin
	sep = ",";
	sepChar = "";
	vec_tmp = new Vector;
	vec_tmp.Push_back("" + sepChar + otk.Account);
	vec_tmp.Push_back("" + sepChar + otk.Action.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + otk.Duration.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + otk.Quantity.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + otk.EndTime.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + numtostr(otk.LimitPrice,3));
	vec_tmp.Push_back("" + sepChar + otk.Symbol.ToString().ToLower());
	vec_tmp.Push_back("" + sepChar + otk.Type.ToString());
	vec_tmp.Push_back("" + sepChar + getOrderName(otk));
	
	Return elstring.Join(",",vec_tmp,0,vec_tmp.Count); 
End;

Method string TrimZero(string num)
Begin
	if(num.Contains("."))then
	Begin
		if(num.SubString(num.Length-1,1).Equals("0") or num.SubString(num.Length-1,1).Equals("."))then
		Begin
			Return TrimZero(num.SubString(0,num.Length-1));
		End
		Else
			return num;
	End
	Else
		Return num;
End;

Method string logg(string infor)
vars:string str;
Begin
	str =  DateTime.Now.ToString()+"|"+infor+".";
	writeStr(LogPath,str+newline,true);
	//print(str);
	Return str;
End;

//log function, two parametres
Method string logg(string infor,object s1)
vars:string str;
Begin
	str =  DateTime.Now.ToString()+"|"+infor+"." + s1.ToString()+".";
	writeStr(LogPath,str+newline,true);
	//print(str);
	Return str;
End;

//log function, two parametres
Method string logg(string infor,object s1,object s2)
vars:string str;
Begin
	str =  DateTime.Now.ToString()+"|"+infor+"." + s1.ToString()+"." + s2.ToString()+".";
	writeStr(LogPath,str+newline,true);
	//print(str);
	Return str;
End;

Method void info(string str)
Begin
	tb_info.Text = DateTime.Now.Format(dtlong) + "|" + str + newline + tb_info.Text;
End;

Method bool writeStr(string filePath,string str,bool append)
vars:StreamWriter sw;
Begin
	if(append)then
	Begin
		Try
			Fileappend(filePath,str);
		Catch(Exception ex)
			logg("【ERROR】FileAppend Error when writeStr",str," 【To】 "+filePath+ ex.Message +" "+ ex.StackTrace);
			Return false;
		End;
	End //If append is TRUE, then append the str to the file
	Else
	Begin
		Try
			sw = StreamWriter.Create(filePath);
			sw.Write(str);
		Catch(elsystem.Exception ex)
			logg("【ERROR】StreamWriter Error when writeStr:",str," 【To】 "+filePath+ ex.Message +" "+ ex.StackTrace);
			Return false;
		End;
	End;
	Return true;
End;

method void tb_KeyPress( elsystem.Object sender, elsystem.windows.forms.KeyPressEventArgs args ) 
vars:TextBox tb_temp;
begin
	tb_temp = sender astype Textbox;
	if(tb_temp.Text.Equals(""))then
	Begin
		If (args.KeyChar < 48 or args.KeyChar > 57) and args.KeyChar <> 8  then
		Begin
			args.Handled = true;
			return;
		End;
	End;

	If (args.KeyChar < 48 or args.KeyChar > 57 )  and args.KeyChar <> 8 then
	Begin
		args.Handled = true; 
	End;
end;

Method void opendig_StatusChanged(elsystem.Object sender,elsystem.EventArgs args)
vars:Vector vec_temp,DataGridViewRow newrow,StreamReader sr,string line_temp,string sym;
Begin
	if(opendig.Status = DialogResult.OK) then
	Begin
		if(opendig.CheckFileExists = true) then
		Begin
			//tb_path.Text = opendig.FileName;
			Try
				sr = StreamReader.create(opendig.FileName);
			Catch(elsystem.Exception ex)
				//lb_info.Text = "打开文件 "+opendig.FileName+" 失败。";
				return;
			End;
			vec_temp = new Vector;
			//dg_symbols.Rows.Clear();
			Try
				while(true)
				Begin
					line_temp = sr.ReadLine();
					if(line_temp.Trim().Equals(""))then
					Begin
						break;
					End
					Else 
					Begin 
						vec_temp = line_temp.Split(",");
						if(vec_temp.Count = 3)then
						Begin
							newrow = DataGridViewRow.Create("");
							//dg_symbols.Rows.Add(newrow);
							//newrow.Resizable = DataGridViewTriState.False;
							//newrow.Cells[1].Value = "";
						End; 
					End;
				End;
			Catch(elsystem.Exception ex)
				//lb_info.Text = logg("[ReadTxt]"+"读取文件"+opendig.FileName+"时出错。");
				return;
			Finally
				if(sr<>null)then
				Begin
					sr.Close();
				End;
			End;
		End;
	End;
End; 

Method string getDesc(string sym)
vars:QuotesProvider QP_temp;
Begin
	if(dict_QPs<>null and dict_QPs.Contains(sym))then
	Begin
		QP_temp = dict_QPs[sym] astype QuotesProvider;
	End
	Else
		QP_temp = null;
	if(QP_temp <> null and QP_temp.State = DataState.loaded)then
	Begin
		Return QP_temp.Quote[QuoteFields.Description].StringValue;
	End;
	Return "";
End;

Method double getLast(string sym)
vars:QuotesProvider QP_temp;
Begin
	if(dict_QPs<>null and dict_QPs.Contains(sym))then
	Begin
		QP_temp = dict_QPs[sym] astype QuotesProvider;
	End
	Else
		QP_temp = null;
	if(QP_temp <> null and QP_temp.State = DataState.loaded)then
	Begin
		Return QP_temp.Quote[QuoteFields.Last].DoubleValue;
	End;
	Return 0;
End;

Method string removeZero(string num)
Begin
	if(num.Contains("."))then
	Begin
		if(num.SubString(num.Length-1,1).Equals("0") or num.SubString(num.Length-1,1).Equals("."))then
		Begin
			Return removeZero(num.SubString(0,num.Length-1));
		End
		Else
			return num;
	End
	Else
		Return num;
End;

Method Account getAccountByID(string acctid)
vars:int loop;
Begin
	For loop = 0 to AP.Count-1
	Begin
		if(AP[loop].AccountID = acctid)then
		Begin
			Return AP[loop];
		End;
	End;
	Return null;
End;

method void cb_account_SelectedIndexChanged( elsystem.Object sender, elsystem.EventArgs args ) 
vars:Account acct,int loop,QuotesProvider QP_temp;
begin
	acct = getAccountByID(cb_account.Text);
	tb_asset.Text = numtostr(acct.RTAccountNetWorth,2);
	tb_cash.Text = numtostr(acct.RTDayTradingBuyingPower,2);
	tb_marketValue.Text = numtostr(acct.RTPositionsMarketValue,2);
	tb_PL.Text = numtostr(acct.RTUnrealizedPL,2);
	PP.Load = FALSE;
	OP.Load = FALSE;
	if(dict_rows = null)then
	Begin
		dict_rows = new Dictionary;;	
	End;
	dict_rows.Clear();
	For loop = dict_QPs.Keys.Count-1 downto 0
	Begin
		QP_temp = dict_QPs[dict_QPs.Keys[loop].ToString()] astype QuotesProvider;
		if(QP_temp<>null)then
		Begin
			QP_temp.Load = false;
			dict_QPs.Remove(dict_QPs.Keys[loop].ToString());
		End;
	End;
	
	PP.Accounts.Clear();
	OP.Accounts.Clear();
	PP.Accounts += cb_account.Text;
	OP.Accounts += cb_account.Text;
	
	setCfgPath(cb_account.Text);
	
	PP.Load = TRUE;
	OP.Load = TRUE;
end;



method void dg_list_CellClick( elsystem.Object sender, elsystem.windows.forms.DataGridViewCellEventArgs args ) 
vars:DataGridViewRow selectedRow,int selectidx,double averPrice,bool checked;
begin
	if(args.RowIndex < 0 )then
	Begin
		print(args.RowIndex);
		return;
	End;
	selectedRow = dg_list.Rows[args.RowIndex];
	averPrice = strtonum(selectedRow.Cells[6].Value.ToString());
	tb_account.Text = selectedRow.Cells[1].Value.ToString();
	tb_symbol.Text = selectedRow.Cells[2].Value.ToString();
	tb_description.Text = selectedRow.Cells[3].Value.ToString();
	tb_quantity.Text = selectedRow.Cells[11].Value.ToString();
	
	if(strtonum(selectedRow.Cells[12].Value.ToString()) <> 0 )then
	Begin
		tb_stopLoss.Text = RemoveZero(numtostr(Round(strtonum(selectedRow.Cells[12].Value.ToString()),2) ,2));
	End
	Else
		tb_stopLoss.Text = "0";
		
	if(strtonum(selectedRow.Cells[13].Value.ToString()) <> 0 )then
	Begin
		tb_stopProfit.Text = RemoveZero(numtostr(Round(strtonum(selectedRow.Cells[13].Value.ToString()),2) ,2));
	End
	Else
		tb_stopProfit.Text = "0";
	tb_trailingStop.Text = selectedRow.Cells[14].Value.ToString();
	cb_trailingStop.Checked = strtobool(selectedRow.Cells[15].Value.ToString());
	rb_long.Checked = strtobool(selectedRow.Cells[17].Value.ToString());
	rb_today.Checked = (strtobool(selectedRow.Cells[17].Value.ToString()) = false);
	
	if(args.ColumnIndex >= 0)then
	Begin
		checked = selectedRow.Cells[0].Value astype bool;
		
		if(rowCheck(selectedRow) = false)then
		Begin
			if(args.ColumnIndex = 0 and checked = FALSE)then
			Begin
				info(selectedRow.Cells[2].Value.ToString() + "," + selectedRow.Cells[3].Value.ToString() + " 配置不正确，请修改.");
			End;
			selectedRow.Cells[0].Value = FALSE;
		End
		Else
		Begin
			selectedRow.Cells[0].Value = (not checked);
		End;
	End;
end;

Method bool rowCheck(DataGridViewRow row)
vars:string quantity,string stopLoss, string stopProfit, string  trailing;
Begin
	quantity = row.Cells[11].Value.ToString();
	stopLoss = row.Cells[12].Value.ToString();
	stopProfit = row.Cells[13].Value.ToString();
	trailing = row.Cells[14].Value.ToString();
	
	if(isWhole100(quantity) = false  or strtonum(quantity) <= 0 or (isLarger0(stopLoss) = false and isLarger0(stopProfit) = false and isLarger0(trailing) = false) )then
	Begin
		Return false;
	End;
	Return true;
End;


Method bool isWhole100(int num)
Begin
	if(intportion(num/100)*100 = num)then
	Begin
		Return TRUE;
	End
	Else
		Return FALSE;
End;

Method bool isWhole100(string num)
Begin
	if(intportion(strtonum(num)/100)*100 = strtonum(num))then
	Begin
		Return TRUE;
	End
	Else
		Return FALSE;
End;

Method bool isLarger0(string num)
Begin
	if(strtonum(num) > 0)then
	Begin
		Return TRUE;
	End
	Else
		Return FALSE;
End;

method void bt_set_Click( elsystem.Object sender, elsystem.EventArgs args ) 
begin
	
	if(isWhole100(tb_quantity.Text) = false  or strtonum(tb_quantity.Text) = 0 )then
	Begin
		info(tb_symbol.Text + " 单笔数量必须为整百且大于0.");
		return;
	End;
	
	//if(isLarger0(tb_stopLoss.Text) = false and isLarger0(tb_stopProfit.Text) = false and isLarger0(tb_trailingStop.Text) = false)then
	//Begin
	//	info(tb_symbol.Text + " 止损、止盈、追踪止损至少其一大于0.");
	//	return;
	//End;
	
	setRow(tb_account.Text,tb_symbol.Text);
end;

method void tb_Point_KeyPress( elsystem.Object sender, elsystem.windows.forms.KeyPressEventArgs args ) 
vars:TextBox tb_temp;
begin
	tb_temp = sender astype Textbox;
	
	
	if(tb_temp.Text.Equals(""))then
	Begin
		If (args.KeyChar < 48 or args.KeyChar > 57) and args.KeyChar <> 8 and args.KeyChar<>46 then
		Begin
			args.Handled = true;
			return;
		End;
	End;
	
	if(tb_temp.Text.Contains("."))then
	Begin
		If (args.KeyChar < 48 or args.KeyChar > 57 )  and args.KeyChar <> 8 then
		Begin
			args.Handled = true; 
		End;
	End
	Else
	Begin
		If (args.KeyChar < 48 or args.KeyChar > 57 )  and args.KeyChar <> 8 and args.KeyChar<>46 then
		Begin
			args.Handled = true; 
		End;
	End;
end;

Method void setRow(string acct,string sym)
vars:DataGridViewRow row,double qty,double avergPrice,double stopLossP,double stopProfitP,double trailingStopP, bool isPercent,bool isLong,bool isMonitor;
Begin
	row = getRow(acct,sym);
	if(row = null)then
	Begin
		info("不存在该条记录.");
		return;
	End;
	isMonitor = strtobool(row.Cells[18].Value.ToString());
	if(isMonitor)then
	Begin
		info(sym + " 正在监控中，请先取消监控再进行配置修改.");
		return;
	End;
	row.Cells[11].Value = tb_quantity.Text;
	avergPrice = strtonum(row.Cells[6].Value.ToString());
	
	//stop loss
	stopLossP = strtonum(tb_stopLoss.Text);
	
	//stop profit
	stopProfitP = strtonum(tb_stopProfit.Text);
	
	row.Cells[12].Value = RemoveZero(tb_StopLoss.Text);
	row.Cells[13].Value = RemoveZero(tb_stopProfit.Text);
	if(strtonum(tb_StopLoss.Text) = 0)then
	Begin
		row.Cells[12].Value = "";
	End;
	if(strtonum(tb_stopProfit.Text) = 0)then
	Begin
		row.Cells[13].Value = "";
	End;
	
	row.Cells[14].Value = tb_trailingStop.Text;
	row.Cells[15].Value = cb_trailingStop.Checked;
	if(rb_long.Checked)then
	Begin
		row.Cells[16].Value = getLast(sym);
	End
	Else
	Begin
		row.Cells[16].Value = "";
	End;
	row.Cells[17].Value = rb_long.Checked;
	
	//save to XML
	saveRowToXML(row);
	
	startMonitor(row);
End;

Method bool saveRowToXML(DataGridViewRow row)
vars:XMLNode node,string acct,string sym;
Begin
	acct = row.Cells[1].Value.ToString();
	sym = row.Cells[2].Value.ToString();
	node = getNode(acct , sym);
	if(node = null)then
	Begin
		addPosition(row);
	End
	Else
	Begin
		updateXMLNode(acct ,sym , row);
		Return true;
	End;
	Return true;
End;

method void bt_cover_Click( elsystem.Object sender, elsystem.EventArgs args )
vars:int loop,OrderTicket otk,bool ismonitor,string acct,string sym,double quantity,double avail,double avgePrice;
begin
	For loop = 0 to dg_list.Rows.Count
	Begin
		acct = dg_list.Rows[loop].Cells[1].Value.ToString();
		sym = dg_list.Rows[loop].Cells[2].Value.ToString();
		avail = strtonum(dg_list.Rows[loop].Cells[5].Value.ToString());
		avgePrice = strtonum(dg_list.Rows[loop].Cells[6].Value.ToString());
		quantity = strtonum(dg_list.Rows[loop].Cells[11].Value.ToString());
		
		ismonitor = strtobool(dg_list.Rows[loop].Cells[18].Value.ToString());
		if(ismonitor = false)then
		Begin
			Continue;
		End;
		otk = new OrderTicket;
		otk.Account = acct;
		otk.Type = tsdata.trading.OrderType.Market;
		otk.Symbol = sym;
		otk.Quantity = minlist(avail,quantity);
		if(avail = 0 or quantity = 0)then
		Begin
			Continue;
		End;
		otk.Action = OrderAction.Sell;
		otk.Duration = "aut";
		otk.Send();
		StopMonitor(dg_list.Rows[loop]);
	End;
	
end;

method void bt_start_Click( elsystem.Object sender, elsystem.EventArgs args ) 
vars:DataGridViewRow selectedRow,int loop ,int count;
begin
	if(dg_list.Rows.Count < 0)then
	Begin
		return;
	End;
	count = 0;
	For loop = 0 to dg_list.Rows.Count
	Begin
		selectedRow = dg_list.Rows[loop];
		if( (selectedRow.Cells[0].Value) astype bool = TRUE)then
		Begin
			if(startMonitor(selectedRow))then
			Begin
				count += 1;
			End;
		End;
	End;
	dg_list.ClearSelection();
	info("开启监控" + numtostr(count,0) + "只代码已生效");
end;

Method bool startMonitor(DataGridViewRow row)
vars:string acct,string sym,double lastP,double avail,double quantity,double stopLossP,double stopProfitP,double trailingStopP,bool isPercent,bool isMonitor,bool isLong,
string desc,OrderTicket otk1,OrderTicket otk2,OrderTicket otk3;
Begin
	acct = row.Cells[1].Value.ToString();
	sym = row.Cells[2].Value.ToString();
	lastP = getLast(sym);
	desc = row.Cells[3].Value.ToString();
	avail = strtonum(row.Cells[5].Value.ToString());
	quantity = strtonum(row.Cells[11].Value.ToString());
	stopLossP = strtonum(row.Cells[12].Value.ToString());
	stopProfitP = strtonum(row.Cells[13].Value.ToString());
	trailingStopP = strtonum(row.Cells[14].Value.ToString());
	isPercent = strtobool(row.Cells[15].Value.ToString()); 
	isLong = strtobool(row.Cells[17].Value.ToString()); 
	isMonitor = strtobool(row.Cells[18].Value.ToString()); 
	
	if(isMonitor)then
	Begin
		info(sym+" 已在监控中.");
		return false;
	End;
	
	if(quantity <= 0)then
	Begin
		info("(" + acct + ","+sym+"参与数量必须大于0.");
		return false;
	End;
	
	if(stopLossP <= 0 and stopProfitP <= 0 and trailingStopP <= 0)then
	Begin
		info("(" + acct + ","+sym+")设置的止损价、止盈价、追踪止损不等均小于等于0.");
		return false;
	End;

	if(isLong and trailingStopP <> 0)then
	Begin
		row.Cells[16].Value = getLast(sym);
	End
	Else
	Begin
		row.Cells[16].Value = "";
	End;
	
	if(StopLossP <> 0 and lastP <= StopLossP)then
	Begin
		Confirm.Text = sym+"("+desc+")";
		lb_confirm_account.Text = acct;
		lb_confirm_sym.Text = sym;
		lb_confirm_last.Text = RemoveZero(numtostr(getLast(sym),4));
		lb_confirm.Text = sym + "最新价" + RemoveZero(numtostr(getLast(sym),4)) + "低于(小于等于)止损价" +  RemoveZero(numtostr(stopLossP,4)) +",是否立即发送止损单？";
		Confirm.Show();
		bt_yes.BackColor = Color.DarkGreen;
		return FALSE;
	End
	Else
	if(StopProfitP <> 0 and lastP >= StopProfitP)then
	Begin
		Confirm.Text = sym+"("+desc+")";
		lb_confirm_account.Text = acct;
		lb_confirm_sym.Text = sym;
		lb_confirm_last.Text = RemoveZero(numtostr(getLast(sym),4));
		lb_confirm.Text = sym + "最新价" + RemoveZero(numtostr(getLast(sym),4)) + "触及(大于等于)止盈价" +  RemoveZero(numtostr(StopProfitP,4)) +",是否立即发送止盈单？";
		Confirm.Show();
		bt_yes.BackColor = Color.Red;
		return FALSE;
	End;
	
	row.Cells[18].Value = TRUE;
	row.Cells[23].Value = DateTime.Now.Format(dtdate);
	saveRowToXML(row);
	
	if(isLong = false)then
	Begin
		//send StopLoss order
		otk1 = new OrderTicket;
		otk1.Account = acct;
		otk1.Type = tsdata.trading.OrderType.StopMarket;
		otk1.SymbolType = tsdata.common.SecurityType.Stock;
		if(stopLossP >= getLast(sym))then
		Begin
			otk1.StopPrice = getLast(sym) - 0.01 ;
		End
		Else
			otk1.StopPrice = stopLossP ;
		otk1.Symbol = sym;
		otk1.Quantity = minlist(avail,quantity);
		otk1.Action = OrderAction.Sell;
		otk1.Duration = "aut";
		otk1.StopPrice = StopLossP;
		otk1.ExtendedProperties.SetItem("OrderName",prefix+"|"+str_Loss + "|"+ DateTime.Now.Format(dtTimeNum));
		
		
		// StopPtofit order
		otk2 = new OrderTicket;
		otk2.Account = acct;
		otk2.Type = tsdata.trading.OrderType.Market;
		otk2.StopPrice = stopProfitP;
		otk2.Symbol = sym;
		otk2.Quantity = minlist(avail,quantity);
		otk2.Action = OrderAction.Sell;
		otk2.IfTouched = True;
		otk2.Duration = "aut";
		otk2.IfTouchedPrice = stopProfitP;
		otk2.IfTouchedPriceStyle = tsdata.trading.PriceStyle.None;
		otk2.IfTouchedPriceOffset = 0;
		otk2.ExtendedProperties.SetItem("OrderName",prefix+"|"+str_Profit + "|" + DateTime.Now.Format(dtTimeNum));
		
		
		//Trailing order
		otk3 = new OrderTicket;
		otk3.Account = acct;
		otk3.Type = tsdata.trading.OrderType.StopMarket;
		otk3.StopPrice = getLast(sym);
		if(isPercent)then
		Begin
			otk3.TrailingStop = tsdata.trading.TrailingStopBehavior.Percentage;
		End
		Else
			otk3.TrailingStop = tsdata.trading.TrailingStopBehavior.Points;
		otk3.TrailingStopAmount = trailingStopP;
		otk3.Symbol = sym;
		otk3.Quantity = minlist(avail,quantity);
		otk3.Action = OrderAction.Sell;
		otk3.IfTouched = True;
		otk3.Duration = "aut";
		otk3.ExtendedProperties.SetItem("OrderName",prefix+"|" + str_Trailing + "|" + DateTime.Now.Format(dtTimeNum));
		
		if( minlist(avail,quantity) > 0)then
		Begin
			if(StopLossP <> 0)then
			Begin
				otk1.Send();	
				logg("[COS-ORDER-SENT]" + OrdertoString(otk1));
			End;
			if(stopProfitP <> 0)then
			Begin
				otk2.Send();	
				logg("[COS-ORDER-SENT]" + OrdertoString(otk2));
			End;
			if(trailingStopP <> 0)then
			Begin
				otk3.Send();	
				logg("[COS-ORDER-SENT]" + OrdertoString(otk3));
			End;
			//print("Order sent.");
		End
		Else
		Begin
			info(sym + "," + desc + " 开启监控失败，可用数量或单位数量无效");
			StopMonitor(row);
			Return false;
		End;
	End;
	
	Return TRUE;
End;

Method void TodayStopLoss()
Begin
	    
End;


method void bt_cancel_Click( elsystem.Object sender, elsystem.EventArgs args ) 
vars:DataGridViewRow selectedRow, int loop ,int count;
begin
	if(dg_list.Rows.Count < 0)then
	Begin
		return;
	End;
	
	count = 0;
	For loop = 0 to dg_list.Rows.Count
	Begin
		selectedRow = dg_list.Rows[loop];
		if((selectedRow.Cells[0].Value) astype bool = TRUE and (selectedRow.Cells[18].Value) astype bool = TRUE)then
		Begin
			StopMonitor(selectedRow);
			count += 1;
		End;
	End;
	dg_list.ClearSelection();
	info("取消监控" + numtostr(count,0) + "只代码已生效");
end;

Method void StopMonitor(DataGridViewRow row)
vars:bool isLong,bool isInMonitor,string orderIDLoss,string orderIDProfit,string orderIDTrailing;
Begin
	//acct = row.Cells[1].Value.ToString();
	//sym = row.Cells[2].Value.ToString();
	isLong = strtobool(row.Cells[17].Value.ToString());
	isInMonitor = strtobool(row.Cells[18].Value.ToString());
	orderIDLoss = row.Cells[20].Value.ToString();
	orderIDProfit = row.Cells[21].Value.ToString();
	orderIDTrailing = row.Cells[22].Value.ToString();
	if(isLong)then //long vaild
	Begin
		row.Cells[16].Value = "";
		row.Cells[18].Value = false;
	End
	Else
	Begin //today
		cancelOrder(orderIDLoss);
		cancelOrder(orderIDProfit);
		cancelOrder(orderIDTrailing);
		row.Cells[16].Value = "";
		row.Cells[18].Value = false;
		row.Cells[20].Value = "";
		row.Cells[21].Value = "";
		row.Cells[22].Value = "";
		row.Cells[23].Value = "";
	End;
	saveRowToXML(row);
End;

Method void cancelOrder(string id)
vars:int loop;
Begin
	if(OP.State = DataState.loaded)then
	Begin
		For loop = 0 to OP.Count-1
		Begin
			if(OP[loop].OrderID = id)then
			Begin
				OP[loop].Cancel();
			End;
		End;
	End;
End;

method void bt_no_Click( elsystem.Object sender, elsystem.EventArgs args ) 
Begin
	Confirm.Visible = false;
end;



method void bt_yes_Click( elsystem.Object sender, elsystem.EventArgs args ) 
vars:DataGridViewRow row,string acct,string sym,double avail,double quantity,OrderTicket otk;
begin
	Confirm.Visible = false;
	acct = lb_confirm_account.Text;
	sym = lb_confirm_sym.Text;
	row = getRow(acct,sym);
	if(row = null)then
	Begin
		info("未找到对应的持仓记录.");
		return;
	End;
	
	avail = strtonum(row.Cells[5].Value.ToString());
	quantity = strtonum(row.Cells[11].Value.ToString());
		
	otk = new OrderTicket;
	otk.Account = acct;
	otk.Type = tsdata.trading.OrderType.Market;
	otk.Symbol = sym;
	otk.Quantity = minlist(avail,quantity);
	if(otk.Quantity > 0)then
	Begin
		otk.Action = OrderAction.Sell;
		otk.Duration = "aut";
		otk.Send();
		logg("[CONFIRM-ORDER-SENT]" + OrdertoString(otk));
	End
	Else
	Begin
		info(sym+"可用数量"+numtostr(avail,0)+"或单笔数量" + numtostr(quantity,0) + "小于等于0.");
	End;
	row.Cells[0].Value = FALSE;
	saveRowToXML(row);
end;

method void AssetGuard_Click( elsystem.Object sender, elsystem.EventArgs args ) 
begin
	dg_list.ClearSelection();
end;



method void bt_selectAll_Click( elsystem.Object sender, elsystem.EventArgs args ) 
vars:int loop;
begin
	for loop = 0 to dg_list.Rows.Count
	Begin
		if(rowCheck(dg_list.Rows[loop]))then
		Begin
			dg_list.Rows[loop].Cells[0].Value = TRUE;
		End;
	End;
end;



method void bt_reverseSelect_Click( elsystem.Object sender, elsystem.EventArgs args ) 
vars:int loop,bool oldValue;
begin
	
	for loop = 0 to dg_list.Rows.Count
	Begin
		oldValue = dg_list.Rows[loop].Cells[0].Value astype bool;
		if(oldValue  = FALSE)then
		Begin
			if(rowCheck(dg_list.Rows[loop]))then
			Begin
				dg_list.Rows[loop].Cells[0].Value = TRUE;
			End;
		End
		Else
		Begin
			dg_list.Rows[loop].Cells[0].Value = FALSE;
		End;
	End;
end;



method void bt_unSelect_Click( elsystem.Object sender, elsystem.EventArgs args ) 
vars:int loop;
begin
	for loop = 0 to dg_list.Rows.Count
	Begin
		dg_list.Rows[loop].Cells[0].Value = false;
	End;
end;

method void bt_Clear_Click( elsystem.Object sender, elsystem.EventArgs args ) 
vars:int loop;
begin
	lb_account.Text = cb_account.Text;
	clearPosition.Show();
end;


method void bt_ClearMarket_Click( elsystem.Object sender, elsystem.EventArgs args ) 
vars:int loop, OrderTicket otk,string sym,double quantity,double availQuantity;
begin
	clearPosition.Visible = FALSE;
	otk = new OrderTicket;
	otk.Account = cb_account.Text;
	otk.BypassClientValidation = TRUE;
	otk.Type = tsdata.trading.OrderType.Market;
	otk.Action = OrderAction.Sell;
	otk.Duration = "aut";
	otk.Type = tsdata.trading.OrderType.Market;
	For loop = 0 to dg_list.Rows.Count
	Begin
		sym = dg_list.Rows[loop].Cells[2].Value.ToString();
		quantity = strtonum(dg_list.Rows[loop].Cells[4].Value.ToString());
		availQuantity = strtonum(dg_list.Rows[loop].Cells[5].Value.ToString());
		otk.Symbol = sym;
		if(availQuantity = quantity)then
		Begin
			otk.Quantity = quantity;
		End
		Else
		Begin
			otk.Quantity = intportion(availQuantity/100)*100;	
		End;
		if(otk.Quantity = 0)then
		Begin
			Continue;
		End;
		otk.Send();
	End;
end;



method void bt_ClearLast_Click( elsystem.Object sender, elsystem.EventArgs args ) 
vars:int loop, OrderTicket otk,string sym,double quantity,double availQuantity;
begin
	clearPosition.Visible = FALSE;
	otk = new OrderTicket;
	otk.Account = cb_account.Text;
	otk.BypassClientValidation = TRUE;
	otk.Type = tsdata.trading.OrderType.Market;
	otk.Action = OrderAction.Sell;
	otk.Duration = "aut";
	otk.Type = tsdata.trading.OrderType.Limit;
	For loop = 0 to dg_list.Rows.Count
	Begin
		sym = dg_list.Rows[loop].Cells[2].Value.ToString();
		quantity = strtonum(dg_list.Rows[loop].Cells[4].Value.ToString());
		availQuantity = strtonum(dg_list.Rows[loop].Cells[5].Value.ToString());
		otk.Symbol = sym;
		if(availQuantity = quantity)then
		Begin
			otk.Quantity = quantity;
		End
		Else
		Begin
			otk.Quantity = intportion(availQuantity/100)*100;	
		End;
		if(otk.Quantity = 0)then
		Begin
			Continue;
		End;
		//otk.LimitPrice = getLast(sym);
		otk.LimitPrice = 9999;
		otk.Send();
	End;
end;


method void bt_ClearCancel_Click( elsystem.Object sender, elsystem.EventArgs args ) 
begin
	clearPosition.Visible = FALSE;
end;

method void bt_about_Click( elsystem.Object sender, elsystem.EventArgs args ) 
begin	
	tb_help.Text = "1、请首先认真阅读App Store附件中该APP使用说明 "+ newline +
				   "2、该APP请务必同时仅开启一个实例，否则可能会出现不可描述的事情" + newline 
				   ;
    about.Show();
end;
