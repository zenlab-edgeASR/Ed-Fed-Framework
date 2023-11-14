from concurrent import futures
import logging
import math
import grpc
import communicate_pb2
import communicate_pb2_grpc
import numpy as np
import pandas as pd
import os
import time
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, TensorDataset
import tensorflow as tf
import pickle


if os.environ.get('https_proxy'):
 del os.environ['https_proxy']
if os.environ.get('http_proxy'):
 del os.environ['http_proxy']

os.environ["CUDA_VISIBLE_DEVICES"] = "1"
gpus = tf.config.list_physical_devices('GPU')
if gpus:
  try:
    # Currently, memory growth needs to be the same across GPUs
    for gpu in gpus:
      tf.config.experimental.set_memory_growth(gpu, True)
    logical_gpus = tf.config.list_logical_devices('GPU')
    print(len(gpus), "Physical GPUs,", len(logical_gpus), "Logical GPUs")
  except RuntimeError as e:
    # Memory growth must be set before GPUs have been initialized
    print(e)





def inv_sherman_morrison(u, A_inv):
    ################## Inverse of a matrix with rank 1 update. ################## 
    Au = np.dot(A_inv, u)
    A_inv -= np.outer(Au, Au)/(1+np.dot(u.T, Au))
    return A_inv


class NeuralNetwork(nn.Module):
    ################## Neural Network architecture for the training time and battery drain prediction ################## 
    def __init__(self):
        super(NeuralNetwork, self).__init__()
        self.fc1 = nn.Linear(4, 32)
        self.fc2 = nn.Linear(32, 16)
        self.fc3 = nn.Linear(16, 2)

    def forward(self, x):
        x = torch.relu(self.fc1(x))
        x = torch.relu(self.fc2(x))
        x = self.fc3(x)
        return x      


class FL(communicate_pb2_grpc.FLServicer):
  count=0
  weight_list=[]
  weight_request=[]
  weight_res=[]
  numpy_li=[]
  k=3
  n=4
  count_nof_clients1 =0 
  count_nof_clients2 =0 
  count_nof_clients3 =0  
  count_nof_clients4 =0  
  count_nof_clients5 =0  
  count_nof_clients6 =0
  criteria_dict={}
  weighted_avg_dict={}
  no_of_rounds=20
  current_round=1
  current_context={}
  current_samples={}
  time_prediction={}
  drop_prediction={}
  batch_size=5
  ucb_shortlist=[]
  b_max={}
  e_max={}
  emin,emax=2,4
  mt=100000000
  current_neural_ucb={}
  keys=[]
  grad_approx_global={}
  threshold=0.15
  d_gamma=0.1
  hp_uc=1
  client_prediction_info={}


  if os.path.exists('./neural_ucb_components/round_info.csv'):
      round_df=pd.read_csv('./neural_ucb_components/round_info.csv')
  else:
      round_data = [1]
      round_df = pd.DataFrame(round_data, columns=['current_round'])
      round_df.to_csv('./neural_ucb_components/round_info.csv')
  print(round_df)
  current_round=float(round_df.loc[0, ['current_round']])
  print('current_round',current_round)
  

  def GetFlWeights(self, request, context):
    ##################  Function to aggregate the weights from the clients and return the aggregated weights ################## 

    arr_of_arrs=request.arr_of_arrs
    recieved_text =  request.flag_id
    ID=int(recieved_text[recieved_text.index('ID:')+len('ID:'):recieved_text.index('$',recieved_text.index('ID:')+len('ID:'))])
    print(ID)
    criteria=float(recieved_text[recieved_text.index('CRITERIA:')+len('CRITERIA:'):recieved_text.index('$',recieved_text.index('CRITERIA:')+len('CRITERIA:'))])
    self.criteria_dict[ID]=criteria
    self.count_nof_clients3+=1

    
    ##################  Aggregation of weights ################## 
    while self.count_nof_clients3<self.k:
        pass  
    self.count+=1
    self.weight_request.append(request)
    denominator=0
    for i in self.criteria_dict.values():
        denominator+=math.exp(1-i)
    for i in self.criteria_dict.keys():
        self.weighted_avg_dict[i]=math.exp(1-self.criteria_dict[i])/denominator
    
    while self.count<self.k:
        pass  
    print('softmax',self.weighted_avg_dict)
    results= communicate_pb2.Weight()
    ser_ver=[]
    for arr in arr_of_arrs:
        inner_type = communicate_pb2.InnerType()
        temp=[]
        for ele in arr.arr:
            inner_type.arr.extend([ele*self.weighted_avg_dict[ID]])
            temp.append(ele*self.weighted_avg_dict[ID])
        results.arr_of_arrs.append(inner_type)
        ser_ver.append(temp)
    self.weight_list.append(ser_ver)
    self.numpy_li.append(np.matrix(ser_ver, dtype=object))
    print('Sending results')
    self.weight_res.append(results)

    while len(self.numpy_li)<self.k:
        pass

    print('numpy_matrix: ',len(self.numpy_li))
    aggregated_weights=[]
    for p in range(len(self.weight_list[0])):
        temp_lis=[]
        for q in range(0,len(self.weight_list[0][p])):
            temp_sum=0
            for r in range(len(self.weight_list)):
                temp_sum+=self.weight_list[r][p][q]
            temp_lis.append(temp_sum)

        aggregated_weights.append(temp_lis)

    print(type(aggregated_weights))
    print(len(aggregated_weights))
    ###########################################################


    ################## Loading the aggregated weights to GRPC object################## 
    result= communicate_pb2.Weight()
    for zz in aggregated_weights:
        inner_type = communicate_pb2.InnerType()
        inner_type.arr.extend(zz)
        result.arr_of_arrs.append(inner_type)
    ####################################################################################


    ################## Saving the aggregated weights ###################################
    if self.count_nof_clients6==0:
        self.count_nof_clients6+=1
        directory = 'neural_ucb_server_weights'
        li=[]
        for filename in os.listdir(directory):
            if 'FL_Weights' in filename and '.pkl' in filename:
                li.append(int(filename[filename.rindex('_')+1:filename.rindex('.')]))
        li.sort()
        new_ckpt_index=li[-1]+1
        new_ckpt_name='FL_Weights_'+str(new_ckpt_index)+'.pkl'
        saving_path="./neural_ucb_server_weights/" +new_ckpt_name
        aggregated_weights=np.array(aggregated_weights)

        with open(saving_path, 'wb') as file:
            pickle.dump(aggregated_weights, file)
        print('Saved updated Global weights: {}'.format(saving_path))
    ####################################################################################
    
    print('sent')
    return result

  
  def GetGlobalWeights(self, request, context):
    ################## Function to send the current global weights to the clients ################## 

    directory = 'neural_ucb_server_weights'
    li=[]
    for filename in os.listdir(directory):
        if 'FL_Weights' in filename and '.pkl' in filename:
            li.append(int(filename[filename.rindex('_')+1:filename.rindex('.')]))
    li.sort()
    pickle_index=li[-1]
    new_pickle_name='FL_Weights_'+str(pickle_index)+'.pkl' # new_ckpt_index
    with open (rf'./neural_ucb_server_weights/{new_pickle_name}', 'rb') as file:
        global_weights = pickle.load(file)
    print(f'selected pickle file:{new_pickle_name}')

    ################## Loading the global weights to GRPC object################## 
    weight= communicate_pb2.Weight()
    count=1
    for i in global_weights:
       inner_type = communicate_pb2.InnerType()
       inner_type.arr.extend(list(i))
       weight.arr_of_arrs.append(inner_type)
       print(i[0],count)
       count+=1
    ####################################################################################
    print(request)
    return weight
    
            


  def client_selection(self,request):
    ################## Function to select clients for the current round################## 

    if self.current_round>=self.no_of_rounds:
        text= communicate_pb2.flag()
        text.flag_id='0:Not selected for training!!'
        return text
    self.count_nof_clients1+=1
    recieved_text =  request.flag_id
    ID=int(recieved_text[recieved_text.index('ID:')+3:recieved_text.index('FLAG:')])
    criteria=recieved_text[recieved_text.index('CRITERIA:')+9:]
    criteria=criteria.strip()
    current_context_info = list(map(float,criteria.split(',')))
    self.current_samples[ID]=current_context_info[0]
    self.current_context[ID]=current_context_info[1:]
    print("current_context_info",self.current_context)


    ################## Training time and Battery drain prediction of each client #################
    model = NeuralNetwork()
    model.load_state_dict(torch.load( "./neural_ucb_components/Model_"+str(ID)+".pt"))
    model.eval()
    y_pred = model.forward(torch.FloatTensor(current_context_info[1:])).squeeze()
    print(f'prediction of {ID}:{y_pred}')
    prediction_time=float(y_pred.detach()[0])
    prediction_drop=float(y_pred.detach()[1])

    self.time_prediction[ID]=prediction_time
    self.drop_prediction[ID]=min(0.00001,prediction_drop)

    print('time_prediction',self.time_prediction)
    print('drop_prediction',self.drop_prediction)

    while self.count_nof_clients1<self.n:
        pass
    ##############################################################################################

    ################## Filtering out clients that can't run e_min epochs #################

    self.b_max[ID]=np.floor((self.current_context[ID][1]-20)/self.drop_prediction[ID])
    self.e_max[ID]=min(self.emax,np.floor(self.b_max[ID]/(self.current_samples[ID]/self.batch_size)))
    if current_context_info[-2]==1:
        self.e_max[ID]=self.emax

    if self.e_max[ID]>=self.emin:
        self.ucb_shortlist.append(ID)
    else:
        text= communicate_pb2.flag()
        text.flag_id='0:Not selected for training!!'
        return text
    
    #########################################################################################

    ################## Selecting top k clients using neural UCB #################
    confidence_multiplier = 1.0
    mu=y_pred
    y_temp_out = torch.FloatTensor([0,0])
    model.zero_grad()
    loss = nn.MSELoss()(y_pred, y_temp_out)
    loss.backward()
    grad_approx = torch.cat(
        [w.grad.detach().flatten() / np.sqrt(w.detach().shape[0]) for w in model.parameters() if w.requires_grad])

    self.grad_approx_global[ID]=grad_approx
    	
    with open (rf'./neural_ucb_components/A_inv{ID}.pkl', 'rb') as file:
        A_inv = pickle.load(file)
    exploration_bonus = min(100000000, confidence_multiplier * np.sqrt(np.dot(grad_approx, np.dot(A_inv, grad_approx.T)))) 
    print(f'exploration_bonus_{ID}:{exploration_bonus}')
    print(f'mu_{ID}:{mu[0].item()}')
    ucb_score = mu[0].item()+exploration_bonus
    self.current_neural_ucb[ID]=ucb_score
    print(self.current_neural_ucb)
    
    self.count_nof_clients4+=1
    self.current_neural_ucb=dict(sorted(self.current_neural_ucb.items(), key=lambda item: item[1],reverse=True))
    self.keys=list(self.current_neural_ucb.keys())
    while self.count_nof_clients4<self.n:
        pass
    
    print('keys',self.keys)
    print('current_neural_ucb',self.current_neural_ucb)
    # print("ID&Index comparsion",ID,self.keys.index(ID),type(ID),type(self.keys.index(ID)))

    if self.keys.index(ID)>=self.k:
        text= communicate_pb2.flag()
        text.flag_id='0:Not selected for training!!'
        return text

    #########################################################################################

    ############################### Assigning Epochs ########################################
    self.mt=min(self.mt,self.e_max[ID]*(self.current_samples[ID]/self.batch_size)*self.time_prediction[ID])
    time.sleep(4)
    e=np.floor((self.mt/self.time_prediction[ID])*(self.batch_size/self.current_samples[ID]))
    text= communicate_pb2.flag()
    #text.flag_id='1:'+str(e)+':Not selected for training!!'
    mode=1
    print(f'Id:{ID} got assigned epochs:{e}')
    text.flag_id='1:'+'$no_of_epochs'+str(int(e))+'$mode'+str(mode)+'$Selected for training!!'

    #########################################################################################
    client_prediction_info_ind=pd.DataFrame()
    client_prediction_info_ind['round']=[self.current_round]
    client_prediction_info_ind['ID']=[ID]
    client_prediction_info_ind['predicted_training_time']=[prediction_time]
    client_prediction_info_ind['predicted_battery_drop']=[prediction_drop]
    client_prediction_info_ind['assigned_epochs']=[e]
    print(ID,':client_selection')
    print(client_prediction_info_ind)
    self.client_prediction_info[ID]=client_prediction_info_ind
    return text
    

  def Multiple_Rounds(self,recieved_text):
    ############################### Function that prepares the framework for next round of FL ########################################
    
    ID=int(recieved_text[recieved_text.index('ID:')+3:recieved_text.index('FLAG:')])
    criteria=recieved_text[recieved_text.index('CRITERIA:')+9:]
    actual_prediction_info = list(map(float,criteria.split(',')))
    self.count_nof_clients2+=1
    print(ID,' entered ',self.count_nof_clients2)
    current_context=self.current_context[ID]
    data={
        'Available_ram': [current_context[0]],
        'charging%': [current_context[1]],
        'is_charging': [current_context[2]],
        'CPU_Util': [current_context[3]],
        'time':[actual_prediction_info[0]],
        'drop':[max(0, actual_prediction_info[1])]
    }
    client_prediction_info_ind=self.client_prediction_info[ID]
    client_prediction_info_ind['actual_training_time']=[actual_prediction_info[0]]
    client_prediction_info_ind['actual_battery_drop']=[actual_prediction_info[1]]
    print(ID,':multple_rounds')
    print(client_prediction_info_ind)
    output_path='./neural_ucb_components/client_prediction_info.csv'
    client_prediction_info_ind.to_csv(output_path, mode='a', header=not os.path.exists(output_path), index=False)
    df = pd.DataFrame(data)
    print(df)

    output_path='./neural_ucb_components/Model_'+str(ID)+'.csv'
    df1=pd.read_csv(output_path)
    df=pd.concat([df1,df])
    df.to_csv(output_path,index=False)

    while self.count_nof_clients2<self.k:
        pass 

    time.sleep(4)
    
    ########################################  Neural nework training of all models ########################################
    model = NeuralNetwork()
    criterion = nn.MSELoss()
    optimizer = optim.Adam(model.parameters(), lr=0.01)
    n_features = 4

    df=pd.read_csv(output_path)
    x = np.zeros([len(df), n_features])
    y = np.zeros([len(df),2])
    df_1=df.sample(frac = 1).reset_index(drop=True)
    for i in range(len(df_1)):
        if(df_1['is_charging'][i]==1):
            is_charging = 1
        else:
            is_charging = 0
            
        context1 = np.array([df_1['Available_ram'][i], df_1['charging%'][i], is_charging, df_1['CPU_Util'][i]])

        x[i,:] = context1
        reward1 = np.array([df_1['time'][i]+0.1*np.random.randn(),df_1['drop'][i]+0.01*np.random.randn()])
        y[i,:] = reward1

    tensor_x=torch.FloatTensor(x)
    tensor_y=torch.FloatTensor(y)
    dataset = TensorDataset(tensor_x, tensor_y)
    dataloader = DataLoader(dataset, batch_size=len(x), shuffle=True)
    for epoch in range(1000):
        for id_batch, (x_batch, y_batch) in enumerate(dataloader):
            y_batch_pred = model(x_batch)
            loss = criterion(y_batch_pred, y_batch)
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

    torch.save(model.state_dict(), "./neural_ucb_components/Model_"+str(ID)+".pt")

    ###############################################################################################################

    ##################################### A_inv Update ############################################################ 
    with open (rf'./neural_ucb_components/A_inv{ID}.pkl', 'rb') as file:
        A_inv = pickle.load(file)  
    A_inv= inv_sherman_morrison(self.grad_approx_global[ID],A_inv)

    with open(rf'./neural_ucb_components/A_inv{ID}.pkl', 'wb') as file:
        pickle.dump(A_inv, file)

    print(ID,' exited ',self.count_nof_clients2)
    ###############################################################################################################

        
    if self.count_nof_clients5==0:
        self.count_nof_clients5+=1
        round_df=pd.read_csv('./neural_ucb_components/round_info.csv')
        round_df['current_round']=[self.current_round+1]
        round_df.to_csv('./neural_ucb_components/round_info.csv',index=False)
    else:
        self.count_nof_clients5+=1

    while self.count_nof_clients5<self.k:
        pass  
    time.sleep(4)
    self.count=0
    self.weight_list=[]
    self.weight_request=[]
    self.weight_res=[]
    self.numpy_li=[]
    self.count_nof_clients1 =0 
    self.count_nof_clients2 =0 
    self.count_nof_clients3 =0 
    self.count_nof_clients4 =0 
    self.count_nof_clients5 =0
    self.count_nof_clients6 =0
    self.criteria_dict={}
    self.weighted_avg_dict={}
    self.current_context={}
    self.current_samples={}
    self.time_prediction={}
    self.drop_prediction={}
    self.grad_approx_global={}
    self.current_neural_ucb={}
    self.client_prediction_info={}
    self.keys=[]
    



  def CommunicatedText(self, request, context):
    ##################################### Function to handle the incoming client messages ############################################################ 

    recieved_text =  request.flag_id
    flag=int(recieved_text[recieved_text.index('FLAG:')+5:recieved_text.index('FLAG:')+6])
    if flag==0:
        text=self.client_selection(request)
        return text
    if flag==1:
        ID=int(recieved_text[recieved_text.index('ID:')+3:recieved_text.index('FLAG:')])
        print(str(ID),':Started Training')
        text= communicate_pb2.flag()
        text.flag_id='Message Recieved!'
        return text
    if flag==2:
        ID=int(recieved_text[recieved_text.index('ID:')+3:recieved_text.index('FLAG:')])
        print(str(ID),':Completed Training and Started sending weights')
        text= communicate_pb2.flag()
        text.flag_id='Message Recieved!'
        return text
    if flag==3:
        self.Multiple_Rounds(recieved_text)
        text= communicate_pb2.flag()
        text.flag_id='System Ready for Next Round!'
        return text
    

def serve():
    ##################################### Function to host the server ############################################################ 

    channel_opt = [('grpc.max_send_message_length', 512 * 1024 * 1024), ('grpc.max_receive_message_length', 1024 * 1024 * 1024)]
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10),options=channel_opt)
    communicate_pb2_grpc.add_FLServicer_to_server(FL(), server)
    server.add_insecure_port('10.114.54.78:50057')
    server.start()
    server.wait_for_termination()


if __name__ == '__main__':
    logging.basicConfig()
    serve()
